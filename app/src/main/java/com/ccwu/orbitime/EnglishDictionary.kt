package com.ccwu.orbitime

import java.util.Locale

object EnglishDictionary {
    private const val MAX_CANDIDATES = 8

    private val exact: Map<String, List<String>> = linkedMapOf(
        "hi" to listOf("hi", "Hi.", "Hi,"),
        "hello" to listOf("hello", "Hello.", "Hello,"),
        "ok" to listOf("OK.", "okay", "Okay."),
        "okay" to listOf("Okay.", "okay"),
        "thanks" to listOf("Thanks.", "Thank you."),
        "thx" to listOf("Thanks.", "Thank you."),
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
        "fix" to listOf("fix", "fix it", "fix this"),
        "bug" to listOf("bug", "bug report"),
        "apk" to listOf("APK", "debug APK"),
        "cmd" to listOf("command", "commands"),
        "repo" to listOf("repository", "repo"),
        "issue" to listOf("issue", "Issue #1"),
        "readme" to listOf("README", "README.md"),
        "privacy" to listOf("privacy", "privacy policy"),
        "build" to listOf("build", "build failed", "build succeeded"),
        "failed" to listOf("failed", "Build failed."),
        "success" to listOf("success", "Build succeeded."),
        "error" to listOf("error", "error log"),
        "log" to listOf("log", "logs"),
        "permission" to listOf("permission", "permissions"),
        "android" to listOf("Android", "Android IME"),
        "keyboard" to listOf("keyboard", "keyboard input"),
        "translate" to listOf("translate", "translation", "Translate this sentence."),
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
    )

    private val commonWords: List<String> = listOf(
        "about", "above", "after", "again", "also", "always", "android", "another", "answer", "anything",
        "because", "before", "better", "build", "button", "change", "check", "clear", "clipboard", "code",
        "command", "complete", "confirm", "copy", "current", "debug", "delete", "direct", "done", "download",
        "english", "error", "explain", "failed", "feature", "file", "final", "finish", "first", "fix",
        "function", "github", "good", "handle", "hello", "help", "issue", "keyboard", "later", "local",
        "message", "method", "need", "next", "okay", "open", "permission", "please", "privacy", "problem",
        "project", "prompt", "readme", "result", "return", "review", "risk", "save", "scope", "search",
        "sentence", "settings", "show", "source", "steps", "success", "switch", "test", "text", "thanks",
        "translate", "translation", "update", "version", "word", "workflow"
    )

    private val commonPhrases: List<String> = listOf(
        "Got it.",
        "Sounds good.",
        "No problem.",
        "Please check it.",
        "Please fix it.",
        "Please confirm first.",
        "Please give actionable steps.",
        "Please list the key issues.",
        "Please do not expand the scope.",
        "I will handle it later.",
        "I will handle it now.",
        "Build failed.",
        "Build succeeded.",
        "The issue is still not fixed.",
        "There is still a problem.",
        "The translation result is missing.",
        "This does not work like a normal keyboard."
    )

    fun candidatesFor(rawInput: String): List<String> {
        val query = normalize(rawInput)
        if (query.isEmpty()) return emptyList()
        val candidates = mutableListOf<String>()
        exact[query]?.let { candidates.addAll(it) }
        commonWords.asSequence()
            .filter { it.startsWith(query) && it != query }
            .take(5)
            .forEach { candidates.add(it) }
        commonPhrases.asSequence()
            .filter { phrase -> phrase.lowercase(Locale.ROOT).startsWith(query) }
            .take(3)
            .forEach { candidates.add(it) }
        candidates.add(rawInput)
        return candidates.distinct().take(MAX_CANDIDATES)
    }

    fun normalize(value: String): String {
        return value.lowercase(Locale.ROOT).filter { it in 'a'..'z' }
    }
}
