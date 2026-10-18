package com.forestry.counter.domain.repository

import com.forestry.counter.domain.model.SurveyContextDraft
import com.forestry.counter.domain.model.SurveyContextRecord

/** Déclarations locales : l'implémentation contrôle la session et capture l'auteur réel. */
interface SurveyContextRepository {
    suspend fun getContext(sessionId: String): SurveyContextRecord?

    /**
     * Une révision null demande une création, jamais un remplacement inconditionnel.
     * Une modification exige la révision positive courante et une session modifiable non scellée.
     */
    suspend fun saveContext(
        sessionId: String,
        draft: SurveyContextDraft,
        expectedRevision: Int?,
    ): SurveyContextRecord

    /**
     * Même contrôle de révision, restreint en plus à la portée du brouillon affiché.
     * La garde n'accorde aucun accès. Une implémentation ancienne échoue sans sauvegarde.
     */
    suspend fun saveContextForScope(
        sessionId: String,
        draft: SurveyContextDraft,
        expectedRevision: Int?,
        requestGuard: () -> Boolean = { true },
    ): SurveyContextRecord = error("SURVEY_CONTEXT_SCOPED_SAVE_NOT_IMPLEMENTED")
}
