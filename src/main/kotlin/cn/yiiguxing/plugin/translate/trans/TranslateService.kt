package cn.yiiguxing.plugin.translate.trans

import cn.yiiguxing.plugin.translate.model.QueryResult
import cn.yiiguxing.plugin.translate.util.LruCache
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger


/**
 * TranslateService
 *
 * Created by Yii.Guxing on 2017/10/30
 */
class TranslateService private constructor() {

    @Volatile
    var translator: Translator = DEFAULT_TRANSLATOR
        private set
    private val cache = LruCache<CacheKey, Translation>(500)

    companion object {
        val DEFAULT_TRANSLATOR: Translator = YoudaoTranslator

        val INSTANCE: TranslateService
            get() = ServiceManager.getService(TranslateService::class.java)

        private val LOGGER = Logger.getInstance(TranslateService::class.java)

        private fun checkThread() = check(ApplicationManager.getApplication().isDispatchThread) {
            "TranslateService must only be used from the Event Dispatch Thread."
        }
    }

    fun setTranslator(translatorId: String) {
        checkThread()
        if (translatorId != translator.id) {
            translator = when (translatorId) {
                YoudaoTranslator.TRANSLATOR_ID -> YoudaoTranslator
                else -> DEFAULT_TRANSLATOR
            }
        }
    }

    fun getCache(text: String, srcLang: Lang? = null, targetLang: Lang? = null): QueryResult? {
        checkThread()

        return null
    }

    fun translate(text: String, srcLang: Lang, targetLang: Lang, listener: TranslateListener) {
        checkThread()

        cache[CacheKey(text, srcLang, targetLang, translator.id)]?.let {
            listener.onSuccess(it)
            return
        }

        ApplicationManager.getApplication().run {
            executeOnPooledThread {
                try {
                    with(translator) {
                        translate(text, srcLang, targetLang).let { translation ->
                            cache.put(CacheKey(text, srcLang, targetLang, id), translation)
                            invokeLater { listener.onSuccess(translation) }
                        }
                    }
                } catch (e: TranslateException) {
                    LOGGER.error("translate", e)
                    invokeLater { listener.onError(e.message) }
                }
            }
        }
    }

    fun translate(text: String, callback: (String, QueryResult?) -> Unit) {
        checkThread()
        ApplicationManager.getApplication().executeOnPooledThread {
            translator.translate(text, Lang.AUTO, Lang.AUTO)
        }
    }

}