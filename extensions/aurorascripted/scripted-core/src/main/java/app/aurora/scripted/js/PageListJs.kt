package app.aurora.scripted.js

import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject

object PageListJs {
    fun evaluate(
        script: String,
        inputJson: String,
        timeoutMs: Long = 5_000,
    ): String {
        val deadline = System.currentTimeMillis() + timeoutMs
        val factory =
            object : ContextFactory() {
                override fun makeContext(): Context {
                    val cx = super.makeContext()
                    cx.instructionObserverThreshold = 10_000
                    return cx
                }

                override fun observeInstructionCount(cx: Context, instructionCount: Int) {
                    if (System.currentTimeMillis() > deadline) {
                        throw IllegalStateException("SCRIPT_ERROR: timeout after ${timeoutMs}ms")
                    }
                }
            }
        val cx = factory.enterContext()
        try {
            cx.optimizationLevel = -1
            val scope: Scriptable = cx.initStandardObjects()
            cx.evaluateString(scope, script, "page_list.js", 1, null)
            if (ScriptableObject.getProperty(scope, "pageList") == Scriptable.NOT_FOUND) {
                throw IllegalStateException("SCRIPT_ERROR: pageList function missing")
            }
            val call =
                """
                (function(){
                  var input = $inputJson;
                  var out = pageList(input);
                  return (typeof out === 'string') ? out : JSON.stringify(out);
                })()
                """.trimIndent()
            val result = cx.evaluateString(scope, call, "pageList_call", 1, null)
            return Context.toString(result)
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException("SCRIPT_ERROR: ${e.message}", e)
        } finally {
            Context.exit()
        }
    }
}
