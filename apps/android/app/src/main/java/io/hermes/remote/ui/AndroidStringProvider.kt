package io.hermes.remote.ui

import android.content.Context
import io.hermes.remote.R
import io.hermes.remote.data.ChatApi

/**
 * Resource-backed [StringProvider].
 *
 * Keeps every user-visible string in the values/ (and values-vi/, values-zh/)
 * string resources, where Android picks the right language for the device. The
 * ViewModel receives this through its factory and never touches a Context itself.
 *
 * [context] is expected to be the application context: this object lives as long
 * as the ViewModel, so holding an Activity would leak it.
 */
class AndroidStringProvider(private val context: Context) : StringProvider {

    override fun quickActions(): List<Pair<String, String>> = listOf(
        context.getString(R.string.quick_status_title) to
            context.getString(R.string.quick_status_prompt),
        context.getString(R.string.quick_files_title) to
            context.getString(R.string.quick_files_prompt),
        context.getString(R.string.quick_test_title) to
            context.getString(R.string.quick_test_prompt),
        context.getString(R.string.quick_git_title) to
            context.getString(R.string.quick_git_prompt),
        context.getString(R.string.quick_shutdown_title) to
            context.getString(R.string.quick_shutdown_prompt),
    )

    override fun missingConfig() = context.getString(R.string.conn_missing_config)

    override fun connectionOk(modelCount: Int, sample: String) =
        context.getString(R.string.conn_ok, modelCount, sample)

    override fun connectionFailed() = context.getString(R.string.conn_failed)

    override fun updateFailed() = context.getString(R.string.update_failed)

    override fun emptyReply() = context.getString(R.string.chat_empty_reply)

    override fun noReplyFromServer() = context.getString(R.string.chat_empty_reply)

    override fun errorSpoken(detail: String) =
        context.getString(R.string.chat_error_spoken, detail)

    override fun noAnswer() = context.getString(R.string.chat_error_no_answer)

    override fun noRecognizer() = context.getString(R.string.voice_err_no_recognizer)

    override fun chatApiMessages() = ChatApi.Messages.from(context)
}
