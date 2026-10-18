package com.forestry.counter.domain.repository

import com.forestry.counter.domain.model.ObservationQualificationDraft
import com.forestry.counter.domain.model.ObservationQualificationRead
import com.forestry.counter.domain.model.ObservationMeasurementBasis

/** Déclarations locales liées à un état brut, avec droits et auteur capturés par le dépôt. */
interface ObservationQualificationRepository {
    /** Une déclaration périmée reste consultable, mais currentDraft est absent. */
    suspend fun getQualification(tigeId: String): ObservationQualificationRead?

    /**
     * Une révision null demande une création, jamais un remplacement sans contrôle.
     * La version brute attendue et la révision déclarative sont vérifiées dans la même transaction.
     * Le formulaire ne fournit ni auteur, ni état brut, ni provenance de copie.
     */
    suspend fun saveQualification(
        tigeId: String,
        draft: ObservationQualificationDraft,
        expectedObservationVersion: Int,
        expectedRevision: Int?,
    ): ObservationQualificationRead

    /**
     * Lit la déclaration de l'état exact affiché, avec une garde capturée à l'ouverture.
     * Une absence de déclaration est null ; un changement de portée, valeurs ou identité échoue.
     * La garde ne peut qu'ajouter un refus aux contrôles canoniques du dépôt.
     * Une implémentation ancienne échoue sans repli vers une lecture non bornée.
     */
    suspend fun getQualificationForObservation(
        expectedBasis: ObservationMeasurementBasis,
        requestGuard: () -> Boolean,
    ): ObservationQualificationRead? = error("OBSERVATION_QUALIFICATION_SCOPED_READ_NOT_IMPLEMENTED")

    /**
     * Sauvegarde une déclaration sur les treize valeurs/version encore identiques à celles affichées.
     * La garde capturée à l'ouverture/rechargement reste obligatoire ; elle n'accorde aucun accès.
     * Une déclaration périmée ne reçoit un nouvel ancrage qu'après lecture explicite de l'observation
     * actualisée et enregistrement de la déclaration.
     */
    suspend fun saveQualificationForObservation(
        expectedBasis: ObservationMeasurementBasis,
        draft: ObservationQualificationDraft,
        expectedRevision: Int?,
        requestGuard: () -> Boolean,
    ): ObservationQualificationRead = error("OBSERVATION_QUALIFICATION_SCOPED_SAVE_NOT_IMPLEMENTED")
}
