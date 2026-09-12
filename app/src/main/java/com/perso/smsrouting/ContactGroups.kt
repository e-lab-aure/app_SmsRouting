package com.perso.smsrouting

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log

/** Lecture des groupes de contacts et des expediteurs qui les composent. */
object ContactGroups {

    private const val TAG = "ContactGroups"

    /**
     * SQLite limite le nombre de parametres lies d'une requete.
     * Les listes d'identifiants sont donc decoupees avant interrogation.
     */
    private const val MAX_ARGS_PER_QUERY = 400

    /**
     * Titres des groupes proposables a l'utilisateur, tries alphabetiquement.
     *
     * Les groupes automatiques (`Mes contacts`, `Favoris`) sont ecartes : ils
     * rassemblent tout ou partie du repertoire et une selection accidentelle
     * rerouterait un volume imprevu de messages.
     */
    fun listGroupTitles(context: Context): List<String> {
        val titles = mutableSetOf<String>()
        query(
            context,
            ContactsContract.Groups.CONTENT_URI,
            arrayOf(ContactsContract.Groups.TITLE),
            "${ContactsContract.Groups.DELETED} = 0 AND " +
                "${ContactsContract.Groups.AUTO_ADD} = 0 AND " +
                "${ContactsContract.Groups.FAVORITES} = 0",
            emptyArray()
        ) { cursor ->
            while (cursor.moveToNext()) {
                cursor.getString(0)?.takeIf { it.isNotBlank() }?.let { titles.add(it) }
            }
        }
        return titles.sortedBy { it.lowercase() }
    }

    /**
     * Renvoie les cles de comparaison ([PhoneNumbers.key]) de tous les
     * expediteurs des contacts membres du groupe portant ce titre.
     *
     * Le meme titre peut exister sur plusieurs comptes (Google, Samsung, SIM):
     * tous les groupes correspondants sont pris en compte.
     */
    fun numberKeysInGroup(context: Context, groupTitle: String): Set<String> {
        val groupIds = groupIdsByTitle(context, groupTitle)
        if (groupIds.isEmpty()) return emptySet()

        val contactIds = contactIdsInGroups(context, groupIds)
        if (contactIds.isEmpty()) return emptySet()

        return phoneKeysOfContacts(context, contactIds)
    }

    private fun groupIdsByTitle(context: Context, groupTitle: String): List<Long> {
        val ids = mutableListOf<Long>()
        query(
            context,
            ContactsContract.Groups.CONTENT_URI,
            arrayOf(ContactsContract.Groups._ID),
            "${ContactsContract.Groups.TITLE} = ? AND ${ContactsContract.Groups.DELETED} = 0",
            arrayOf(groupTitle)
        ) { cursor ->
            while (cursor.moveToNext()) {
                ids.add(cursor.getLong(0))
            }
        }
        return ids
    }

    private fun contactIdsInGroups(context: Context, groupIds: List<Long>): List<Long> {
        val ids = mutableSetOf<Long>()
        groupIds.chunked(MAX_ARGS_PER_QUERY).forEach { chunk ->
            val args = buildList {
                add(ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE)
                chunk.forEach { add(it.toString()) }
            }
            query(
                context,
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data.CONTACT_ID),
                "${ContactsContract.Data.MIMETYPE} = ? AND " +
                    "${ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID} IN (${placeholders(chunk.size)})",
                args.toTypedArray()
            ) { cursor ->
                while (cursor.moveToNext()) {
                    ids.add(cursor.getLong(0))
                }
            }
        }
        return ids.toList()
    }

    private fun phoneKeysOfContacts(context: Context, contactIds: List<Long>): Set<String> {
        val keys = mutableSetOf<String>()
        contactIds.chunked(MAX_ARGS_PER_QUERY).forEach { chunk ->
            query(
                context,
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} IN (${placeholders(chunk.size)})",
                chunk.map { it.toString() }.toTypedArray()
            ) { cursor ->
                while (cursor.moveToNext()) {
                    val key = PhoneNumbers.key(cursor.getString(0))
                    if (key.isNotEmpty()) keys.add(key)
                }
            }
        }
        return keys
    }

    private fun placeholders(count: Int): String = List(count) { "?" }.joinToString(",")

    /**
     * Execute une requete sur le fournisseur de contacts.
     * Une erreur de lecture ne doit jamais faire echouer le traitement d'un SMS:
     * elle est tracee et traitee comme un resultat vide.
     */
    private fun query(
        context: Context,
        uri: Uri,
        projection: Array<String>,
        selection: String,
        selectionArgs: Array<String>,
        block: (Cursor) -> Unit
    ) {
        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use(block)
        } catch (e: SecurityException) {
            Log.w(TAG, "Acces aux contacts refuse", e)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Requete contacts invalide", e)
        }
    }
}
