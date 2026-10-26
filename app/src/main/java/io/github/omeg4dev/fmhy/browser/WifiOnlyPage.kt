package io.github.omeg4dev.fmhy.browser

/**
 * Generates an in-app message page shown when "Browse only on Wi-Fi" is enabled and the device
 * is currently on mobile data or metered Wi-Fi.
 */
object WifiOnlyPage {

    fun html(
        isDark: Boolean,
        isBlack: Boolean,
        accentHex: String,
        surfaceHex: String,
        onSurfaceHex: String,
        bridgeName: String? = null,
    ): String {
        val bg = if (isBlack) "#000000" else if (isDark) "#121212" else "#f5f5f5"
        val cardBg = surfaceHex
        val cardBorder = if (isBlack) "border: 1px solid rgba(255, 255, 255, 0.12);" else ""
        val secondaryBg = if (isDark) "rgba(255, 255, 255, 0.08)" else "rgba(0, 0, 0, 0.05)"
        val secondaryBorder = if (isDark) "rgba(255, 255, 255, 0.15)" else "rgba(0, 0, 0, 0.15)"
        val directBridge = bridgeName.orEmpty()

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>Browse on Wi-Fi Only</title>
              <style>
                body {
                  font-family: system-ui, -apple-system, sans-serif;
                  background: $bg;
                  color: $onSurfaceHex;
                  display: flex;
                  align-items: center;
                  justify-content: center;
                  min-height: 100vh;
                  margin: 0;
                  padding: 16px;
                  box-sizing: border-box;
                }
                .card {
                  background: $cardBg;
                  padding: 28px 24px;
                  border-radius: 16px;
                  max-width: 420px;
                  width: 100%;
                  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.35);
                  text-align: center;
                  box-sizing: border-box;
                  $cardBorder
                }
                .icon {
                  margin-bottom: 16px;
                  display: inline-block;
                }
                h1 {
                  font-size: 20px;
                  font-weight: 600;
                  line-height: 1.35;
                  margin: 0 0 8px;
                  color: $onSurfaceHex;
                }
                p {
                  font-size: 14px;
                  line-height: 1.5;
                  color: $onSurfaceHex;
                  opacity: 0.8;
                  margin: 0 0 24px;
                }
                .buttons {
                  display: flex;
                  flex-direction: column;
                  gap: 12px;
                }
                button {
                  padding: 13px 16px;
                  border-radius: 10px;
                  font-size: 14px;
                  font-weight: 600;
                  cursor: pointer;
                  box-sizing: border-box;
                  width: 100%;
                  border: none;
                  transition: opacity 0.2s ease;
                }
                button:active {
                  opacity: 0.8;
                }
                .btn-primary {
                  background: $accentHex;
                  color: #ffffff;
                }
                .btn-secondary {
                  background: $secondaryBg;
                  color: $onSurfaceHex;
                  border: 1px solid $secondaryBorder;
                }
              </style>
            </head>
            <body>
              <div class="card">
                <h1>Browsing is set to Wi-Fi only.</h1>
                <p>Connect to an unmetered Wi-Fi network, or choose an option below to proceed.</p>
                <div class="buttons">
                  <button class="btn-primary" onclick="useMobileDataThisTime()">Use mobile data this time</button>
                  <button class="btn-secondary" onclick="openDataUsageSettings()">Open settings</button>
                </div>
              </div>
              <script>
                function getBridge() {
                  const bName = '$directBridge';
                  if (bName && window[bName]) return window[bName];
                  for (const k in window) {
                    if (k.startsWith('_fmhy') && window[k] && typeof window[k].useMobileDataThisTime === 'function') {
                      return window[k];
                    }
                  }
                  return null;
                }
                function useMobileDataThisTime() {
                  const b = getBridge();
                  if (b && typeof b.useMobileDataThisTime === 'function') {
                    b.useMobileDataThisTime();
                  }
                }
                function openDataUsageSettings() {
                  const b = getBridge();
                  if (b && typeof b.openDataUsageSettings === 'function') {
                    b.openDataUsageSettings();
                  }
                }
              </script>
            </body>
            </html>
        """.trimIndent()
    }
}
