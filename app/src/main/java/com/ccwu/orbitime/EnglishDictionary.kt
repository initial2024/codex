package com.ccwu.orbitime

import kotlin.math.abs
import kotlin.math.min

object EnglishDictionary {
    private const val MAX_CANDIDATES = 12

    private val exact: Map<String, List<String>> = linkedMapOf(
        "hi" to listOf("hi", "Hi.", "Hi,"),
        "hello" to listOf("hello", "Hello.", "Hello,"),
        "hey" to listOf("Hey.", "Hey,"),
        "ok" to listOf("OK.", "okay", "Okay."),
        "okay" to listOf("Okay.", "okay"),
        "yes" to listOf("Yes.", "yes"),
        "no" to listOf("No.", "no"),
        "thanks" to listOf("Thanks.", "Thank you."),
        "thank" to listOf("Thank you.", "thanks"),
        "thx" to listOf("Thanks.", "Thank you."),
        "sorry" to listOf("Sorry.", "I'm sorry."),
        "gm" to listOf("Good morning.", "Good morning,"),
        "gn" to listOf("Good night.", "Good night,"),
        "brb" to listOf("Be right back."),
        "asap" to listOf("as soon as possible"),
        "btw" to listOf("by the way"),
        "imo" to listOf("in my opinion"),
        "idk" to listOf("I don't know."),
        "np" to listOf("No problem."),
        "nvm" to listOf("Never mind."),
        "whq" to listOf("who", "what", "where", "when", "why", "which", "what happened?"),
        "wdy" to listOf("What do you mean?"),
        "wyd" to listOf("What are you doing?"),
        "wip" to listOf("work in progress"),
        "todo" to listOf("TODO:", "to do"),
        "fix" to listOf("fix", "fix it", "fix this", "Please fix it."),
        "bug" to listOf("bug", "bug report"),
        "apk" to listOf("APK", "debug APK"),
        "cmd" to listOf("command", "commands"),
        "repo" to listOf("repository", "repo"),
        "issue" to listOf("issue", "Issue #1"),
        "readme" to listOf("README", "README.md"),
        "privacy" to listOf("privacy", "privacy policy"),
        "build" to listOf("build", "build failed", "build succeeded", "build the APK"),
        "failed" to listOf("failed", "Build failed."),
        "success" to listOf("success", "Build succeeded."),
        "error" to listOf("error", "error log"),
        "log" to listOf("log", "logs"),
        "permission" to listOf("permission", "permissions"),
        "android" to listOf("Android", "Android IME"),
        "keyboard" to listOf("keyboard", "keyboard input"),
        "translate" to listOf("translate", "translation", "Translate this sentence."),
        "translation" to listOf("translation", "translation result"),
        "please" to listOf("please", "Please"),
        "check" to listOf("check", "Please check it."),
        "confirm" to listOf("confirm", "Please confirm."),
        "review" to listOf("review", "Please review it."),
        "update" to listOf("update", "Please update it."),
        "send" to listOf("send", "Please send it."),
        "later" to listOf("later", "I will handle it later."),
        "now" to listOf("now", "I will handle it now."),
        "scope" to listOf("scope", "Do not expand the scope."),
        "steps" to listOf("steps", "Please give actionable steps."),
        "risk" to listOf("risk", "Please list the risks."),
        "local" to listOf("local", "local-only", "local data"),
        "offline" to listOf("offline", "offline translation"),
        "candidate" to listOf("candidate", "candidate selection"),
        "dictionary" to listOf("dictionary", "user dictionary"),
        "phrase" to listOf("phrase", "quick phrase"),
        "sentence" to listOf("sentence", "full sentence"),
        "professional" to listOf("professional", "professional version"),
        "usable" to listOf("usable", "make it usable"),
        "improve" to listOf("improve", "improve it"),
        "optimize" to listOf("optimize", "optimization"),
        "fuzzy" to listOf("fuzzy", "fuzzy matching"),
        "correct" to listOf("correct", "correction", "auto-correction"),
    )

    private val typoCorrections: Map<String, List<String>> = linkedMapOf(
        "teh" to listOf("the"),
        "adn" to listOf("and"),
        "dont" to listOf("don't"),
        "cant" to listOf("can't"),
        "wont" to listOf("won't"),
        "im" to listOf("I'm"),
        "ive" to listOf("I've"),
        "youre" to listOf("you're"),
        "thats" to listOf("that's"),
        "heres" to listOf("here's"),
        "pls" to listOf("please", "Please"),
        "plz" to listOf("please", "Please"),
        "thansk" to listOf("thanks", "Thanks."),
        "trasnlate" to listOf("translate", "translation"),
        "tranlate" to listOf("translate", "translation"),
        "permision" to listOf("permission", "permissions"),
        "succes" to listOf("success", "Build succeeded."),
        "sucess" to listOf("success", "Build succeeded."),
        "fialed" to listOf("failed", "Build failed."),
        "faild" to listOf("failed", "Build failed."),
    )

    private val commonWords: List<String> = listOf(
        "about", "above", "after", "again", "also", "always", "android", "another", "answer", "anything",
        "because", "before", "better", "build", "button", "candidate", "change", "check", "clear", "clipboard", "code",
        "command", "complete", "confirm", "copy", "correct", "current", "debug", "delete", "direct", "dictionary", "done", "download",
        "english", "error", "explain", "failed", "feature", "file", "final", "finish", "first", "fix", "fuzzy",
        "function", "github", "good", "handle", "hello", "help", "improve", "issue", "keyboard", "later", "local",
        "message", "method", "need", "next", "offline", "okay", "open", "permission", "phrase", "please", "privacy", "problem",
        "professional", "project", "prompt", "readme", "result", "return", "review", "risk", "save", "scope", "search",
        "sentence", "settings", "show", "source", "steps", "success", "switch", "test", "text", "thanks",
        "translate", "translation", "update", "usable", "version", "word", "workflow"
    )

    private val commonPhrases: List<String> = listOf(
        "Got it.",
        "Okay.",
        "Sounds good.",
        "No problem.",
        "Please check it.",
        "Please fix it.",
        "Please confirm first.",
        "Please give actionable steps.",
        "Please list the key issues.",
        "Please do not expand the scope.",
        "Please make the minimum necessary fix.",
        "Please update the README.",
        "Please update the privacy policy.",
        "Please build the debug APK.",
        "I will handle it later.",
        "I will handle it now.",
        "Build failed.",
        "Build succeeded.",
        "The issue is still not fixed.",
        "There is still a problem.",
        "The translation result is missing.",
        "This does not work like a normal keyboard.",
        "The candidate database is not enough.",
        "Please add more local data.",
        "Make it closer to a professional version.",
        "Do not add the INTERNET permission.",
        "Do not add cloud translation.",
        "Do not add analytics or ads."
    )

    fun candidatesFor(rawInput: String): List<String> {
        val query = normalize(rawInput)
        if (query.isEmpty()) return emptyList()
        val candidates = mutableListOf<String>()
        exact[query]?.let { candidates.addAll(it) }
        typoCorrections[query]?.let { candidates.addAll(it) }
        commonWords.asSequence()
            .filter { it.startsWith(query) && it != query }
            .take(6)
            .forEach { candidates.add(it) }
        commonPhrases.asSequence()
            .filter { phrase -> phrase.lowercase().startsWith(query) }
            .take(4)
            .forEach { candidates.add(it) }
        if (candidates.size < 4 && query.length >= 3) {
            commonWords.asSequence()
                .filter { boundedDistance(query, it, if (query.length <= 5) 1 else 2) <= if (query.length <= 5) 1 else 2 }
                .take(5)
                .forEach { candidates.add(it) }
        }
        candidates.add(rawInput)
        return candidates.distinct().take(MAX_CANDIDATES)
    }

    fun normalize(value: String): String {
        return value.lowercase().filter { it in 'a'..'z' }
    }

    private fun boundedDistance(a: String, b: String, limit: Int): Int {
        if (abs(a.length - b.length) > limit) return limit + 1
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            var rowMin = current[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = min(min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost)
                rowMin = min(rowMin, current[j])
            }
            if (rowMin > limit) return limit + 1
            val tmp = previous
            previous = current
            current = tmp
        }
        return previous[b.length]
    }
}
