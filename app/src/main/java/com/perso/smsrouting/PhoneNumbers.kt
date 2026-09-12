package com.perso.smsrouting

import java.text.Normalizer
import java.util.Locale

/**
 * Normalisation des expediteurs de SMS pour la comparaison.
 *
 * Deux formes d'expediteur coexistent dans un repertoire :
 * - des numeros, qu'un meme correspondant peut ecrire de plusieurs facons
 *   (0612345678, +33612345678, 0033 6 12 34 56 78) ;
 * - des identifiants alphanumeriques utilises par les services (Swile, Ricardo),
 *   qui ne sont pas des numeros mais peuvent etre enregistres comme contacts.
 *
 * Les deux especes sont volontairement separees par un prefixe de cle pour
 * qu'un identifiant textuel ne puisse jamais entrer en collision avec un numero.
 */
object PhoneNumbers {

    /** Longueur d'un numero national sans indicatif (France: 612345678). */
    private const val SIGNIFICANT_DIGITS = 9

    /**
     * En dessous de ce nombre de chiffres, une valeur contenant des lettres est
     * traitee comme un identifiant de service et non comme un numero mal ecrit.
     */
    private const val MIN_DIGITS_FOR_NUMBER = 6

    private const val NUMERIC_PREFIX = "N:"
    private const val TEXT_PREFIX = "A:"

    /**
     * Renvoie la cle de comparaison d'un expediteur, ou une chaine vide si la
     * valeur ne contient ni chiffre ni lettre.
     */
    fun key(raw: String?): String {
        if (raw.isNullOrBlank()) return ""

        val digits = raw.filter { it.isDigit() }
        if (digits.length >= MIN_DIGITS_FOR_NUMBER) {
            return NUMERIC_PREFIX + digits.takeLast(SIGNIFICANT_DIGITS)
        }

        val letters = textKey(raw)
        if (letters.isNotEmpty()) return TEXT_PREFIX + letters

        return if (digits.isNotEmpty()) NUMERIC_PREFIX + digits else ""
    }

    /**
     * Cle d'un identifiant alphanumerique : majuscules, sans accent ni
     * separateur, pour absorber les variations d'ecriture entre le repertoire
     * et l'en-tete du SMS recu.
     */
    private fun textKey(raw: String): String {
        if (raw.none { it.isLetter() }) return ""
        return Normalizer.normalize(raw, Normalizer.Form.NFD)
            .filter { it.isLetterOrDigit() }
            .uppercase(Locale.ROOT)
    }

    /** Compare deux expediteurs sans tenir compte du format d'ecriture. */
    fun sameNumber(a: String?, b: String?): Boolean {
        val keyA = key(a)
        val keyB = key(b)
        return keyA.isNotEmpty() && keyA == keyB
    }
}
