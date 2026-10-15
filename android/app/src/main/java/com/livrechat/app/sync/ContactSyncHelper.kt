package com.livrechat.app.sync

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import android.util.Log
import androidx.core.content.ContextCompat
import com.livrechat.app.LivreChatApp
import com.livrechat.app.data.local.entity.ContactEntity
import com.livrechat.app.data.remote.model.DeviceContactPayload
import com.livrechat.app.data.remote.model.MatchedContactDto
import com.livrechat.app.data.remote.model.SyncContactsRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ContactSyncHelper {
    private const val TAG = "ContactSyncHelper"

    const val ACCOUNT_TYPE = "com.livrechat.app"
    const val ACCOUNT_NAME = "LivreChat"

    const val MIME_CHAT = "vnd.android.cursor.item/vnd.com.livrechat.app.chat"
    const val MIME_CALL = "vnd.android.cursor.item/vnd.com.livrechat.app.call"

    fun getOrCreateAccount(context: Context): Account? {
        return try {
            val accountManager = AccountManager.get(context)
            val accounts = accountManager.getAccountsByType(ACCOUNT_TYPE)
            if (accounts.isNotEmpty()) {
                accounts[0]
            } else {
                val account = Account(ACCOUNT_NAME, ACCOUNT_TYPE)
                val added = accountManager.addAccountExplicitly(account, null, null)
                if (added) {
                    ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 1)
                    ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, true)
                }
                account
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao obter ou criar conta de sincronização: ${e.message}")
            null
        }
    }

    fun requestSync(context: Context) {
        val account = getOrCreateAccount(context) ?: return
        val bundle = Bundle().apply {
            putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
            putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
        }
        ContentResolver.requestSync(account, ContactsContract.AUTHORITY, bundle)
    }

    suspend fun syncDeviceContacts(context: Context): Pair<Int, List<MatchedContactDto>> = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Permissão READ_CONTACTS não concedida.")
            return@withContext Pair(0, emptyList())
        }

        val app = LivreChatApp.instance
        val apiService = app.apiClient.apiService
        val db = app.database

        // Ensure Android Account exists
        val account = getOrCreateAccount(context)

        // 1. Read all contacts and phone numbers from device
        val contactsMap = mutableMapOf<String, MutableList<String>>() // Name -> Phones
        val projection = arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER)

        try {
            context.contentResolver.query(
                Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${Phone.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(Phone.DISPLAY_NAME)
                val numberIdx = cursor.getColumnIndex(Phone.NUMBER)

                while (cursor.moveToNext()) {
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                    val number = if (numberIdx >= 0) cursor.getString(numberIdx) ?: "" else ""
                    if (number.isNotBlank()) {
                        val key = if (name.isNotBlank()) name else number
                        val list = contactsMap.getOrPut(key) { mutableListOf() }
                        if (!list.contains(number)) {
                            list.add(number)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro lendo contatos do celular: ${e.message}")
            return@withContext Pair(0, emptyList())
        }

        if (contactsMap.isEmpty()) {
            return@withContext Pair(0, emptyList())
        }

        // 2. Build payload for backend sync
        val payloadList = contactsMap.map { (name, phones) ->
            DeviceContactPayload(name = name, phones = phones)
        }

        val matchedContacts: List<MatchedContactDto>
        try {
            val response = apiService.syncContacts(SyncContactsRequest(contacts = payloadList))
            if (!response.isSuccessful || response.body() == null) {
                Log.w(TAG, "Servidor retornou erro na sincronização: ${response.code()}")
                return@withContext Pair(0, emptyList())
            }
            matchedContacts = response.body()!!.contacts
        } catch (e: Exception) {
            Log.e(TAG, "Falha na chamada de API syncContacts: ${e.message}")
            return@withContext Pair(0, emptyList())
        }

        if (matchedContacts.isEmpty()) {
            return@withContext Pair(0, emptyList())
        }

        // 3. Save into local Room database
        for (m in matchedContacts) {
            db.contactDao().insertContact(
                ContactEntity(
                    id = m.contactUserId,
                    contactUserId = m.contactUserId,
                    username = m.username,
                    displayName = m.displayName,
                    avatarUrl = m.avatarUrl,
                    publicKey = m.publicKey,
                    bio = m.bio
                )
            )
        }

        // 4. Inject WhatsApp-style actions into Android Native Contacts
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            injectNativeContactEntries(context, matchedContacts)
        }

        return@withContext Pair(matchedContacts.size, matchedContacts)
    }

    private fun injectNativeContactEntries(context: Context, matchedList: List<MatchedContactDto>) {
        try {
            for (contact in matchedList) {
                val userId = contact.contactUserId
                val displayName = contact.displayName
                val phone = contact.matchedPhone ?: contact.phoneNumber ?: ""

                // Check if RawContact already exists for this LivreChat user
                var existingRawContactId: Long? = null
                val rawUri = RawContacts.CONTENT_URI.buildUpon()
                    .appendQueryParameter(RawContacts.ACCOUNT_NAME, ACCOUNT_NAME)
                    .appendQueryParameter(RawContacts.ACCOUNT_TYPE, ACCOUNT_TYPE)
                    .build()

                context.contentResolver.query(
                    rawUri,
                    arrayOf(RawContacts._ID),
                    "${RawContacts.SYNC1} = ?",
                    arrayOf(userId),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        existingRawContactId = cursor.getLong(0)
                    }
                }

                val ops = ArrayList<ContentProviderOperation>()

                if (existingRawContactId == null) {
                    // Create new RawContact
                    val rawIndex = ops.size
                    ops.add(
                        ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                            .withValue(RawContacts.ACCOUNT_NAME, ACCOUNT_NAME)
                            .withValue(RawContacts.ACCOUNT_TYPE, ACCOUNT_TYPE)
                            .withValue(RawContacts.SYNC1, userId)
                            .withValue(RawContacts.SOURCE_ID, userId)
                            .build()
                    )

                    // Add StructuredName
                    ops.add(
                        ContentProviderOperation.newInsert(Data.CONTENT_URI)
                            .withValueBackReference(Data.RAW_CONTACT_ID, rawIndex)
                            .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                            .withValue(StructuredName.DISPLAY_NAME, displayName)
                            .build()
                    )

                    // Add Phone number
                    if (phone.isNotBlank()) {
                        ops.add(
                            ContentProviderOperation.newInsert(Data.CONTENT_URI)
                                .withValueBackReference(Data.RAW_CONTACT_ID, rawIndex)
                                .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                                .withValue(Phone.NUMBER, phone)
                                .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
                                .build()
                        )
                    }

                    // Add LivreChat Chat Action (WhatsApp style)
                    ops.add(
                        ContentProviderOperation.newInsert(Data.CONTENT_URI)
                            .withValueBackReference(Data.RAW_CONTACT_ID, rawIndex)
                            .withValue(Data.MIMETYPE, MIME_CHAT)
                            .withValue(Data.DATA1, userId)
                            .withValue(Data.DATA2, "Mensagem para $displayName")
                            .withValue(Data.DATA3, "LivreChat")
                            .build()
                    )

                    // Add LivreChat Call Action (WhatsApp style)
                    ops.add(
                        ContentProviderOperation.newInsert(Data.CONTENT_URI)
                            .withValueBackReference(Data.RAW_CONTACT_ID, rawIndex)
                            .withValue(Data.MIMETYPE, MIME_CALL)
                            .withValue(Data.DATA1, userId)
                            .withValue(Data.DATA2, "Chamada de Voz LivreChat")
                            .withValue(Data.DATA3, "LivreChat")
                            .build()
                    )
                } else {
                    // Update existing actions
                    ops.add(
                        ContentProviderOperation.newUpdate(Data.CONTENT_URI)
                            .withSelection(
                                "${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} = ?",
                                arrayOf(existingRawContactId.toString(), MIME_CHAT)
                            )
                            .withValue(Data.DATA1, userId)
                            .withValue(Data.DATA2, "Mensagem para $displayName")
                            .withValue(Data.DATA3, "LivreChat")
                            .build()
                    )
                }

                if (ops.isNotEmpty()) {
                    try {
                        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                    } catch (opErr: Exception) {
                        Log.w(TAG, "Erro aplicando batch de contatos para $displayName: ${opErr.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao injetar contatos no provedor nativo: ${e.message}")
        }
    }

    fun openAddContactInPhoneBook(context: Context, name: String? = null, phone: String? = null) {
        try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                type = ContactsContract.Contacts.CONTENT_TYPE
                if (!name.isNullOrBlank()) {
                    putExtra(ContactsContract.Intents.Insert.NAME, name)
                }
                if (!phone.isNullOrBlank()) {
                    putExtra(ContactsContract.Intents.Insert.PHONE, phone)
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Não foi possível abrir o app de contatos do celular: ${e.message}")
        }
    }
}
