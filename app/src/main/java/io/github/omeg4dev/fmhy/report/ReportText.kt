package io.github.omeg4dev.fmhy.report

object ReportText {
    fun build(draft: ReportDraft): String {
        val sb = StringBuilder()

        // 1. ## What happened
        sb.append("## What happened\n")
        sb.append("Area: ").append(draft.area.label).append("\n")
        val whatWentWrong = draft.whatWentWrong.trim()
        if (whatWentWrong.isEmpty()) {
            sb.append("(not described)")
        } else {
            sb.append(whatWentWrong)
        }
        sb.append("\n\n")

        // 2. ## What was expected
        sb.append("## What was expected\n")
        val expected = draft.expected.trim()
        if (expected.isEmpty()) {
            sb.append("(not described)")
        } else {
            sb.append(expected)
        }

        // 3. ## Technical details (only when includeTechnical or crash != null)
        if (draft.includeTechnical || draft.crash != null) {
            sb.append("\n\n## Technical details\n")
            val tech = draft.technical
            sb.append("App version: ").append(tech?.appVersion ?: "Unknown").append("\n")
            sb.append("Android version: ").append(tech?.androidVersion ?: "Unknown").append("\n")
            sb.append("Device model: ").append(tech?.deviceModel ?: "Unknown")

            if (draft.crash != null) {
                sb.append("\nCrash: ").append(draft.crash.exceptionClass)
                sb.append("\nWhere:")
                if (draft.crash.frames.isNotEmpty()) {
                    for (frame in draft.crash.frames.take(8)) {
                        sb.append("\n").append(frame)
                    }
                } else {
                    sb.append(" (none)")
                }
            }
        }

        // 4. ## Page (only when includePageUrl && pageUrl != null && !isPrivateTab && crash == null)
        // Private tab: the page address is NEVER included even if includePageUrl is true.
        // Crash reports never contain a page address.
        if (draft.includePageUrl && draft.pageUrl != null && !draft.isPrivateTab && draft.crash == null) {
            sb.append("\n\n## Page\n")
            sb.append("Web address: ").append(draft.pageUrl)
        }

        return sb.toString()
    }
}
