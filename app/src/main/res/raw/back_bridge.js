if (loadingState is LoadingState.Finished) {
    // 1. Обработка кнопки "Назад" (Эмуляция Escape)
    val backPressScript = """
        (function() {
            function dispatchKey(key, keyCode, code) {
                const downEvent = new KeyboardEvent('keydown', {
                    key: key, keyCode: keyCode, code: code, which: keyCode,
                    bubbles: true, cancelable: true
                });
                const upEvent = new KeyboardEvent('keyup', {
                    key: key, keyCode: keyCode, code: code, which: keyCode,
                    bubbles: true, cancelable: true
                });
                document.dispatchEvent(downEvent);
                document.dispatchEvent(upEvent);
            }
            dispatchKey('Escape', 27, 'Escape');
        })();
    """.trimIndent()

    // 2. Выполняем скрипт кнопки "Назад"
    navigator.evaluateJavaScript(backPressScript)

    // 3. Выполняем удалённый или локальный скрипт (если есть)
    if (jsScript != null) {
        navigator.evaluateJavaScript(jsScript)
    }

    // 4. Выполняем наш блокировщик для Shorts (из res/raw/adblock.js)
    try {
        val adblockJs = readRaw(context, R.raw.adblock)
        navigator.evaluateJavaScript(adblockJs)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
