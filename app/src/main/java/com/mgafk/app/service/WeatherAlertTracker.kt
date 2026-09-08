package com.mgafk.app.service

/**
 * Decides whether a reported weather change is one the app has not seen yet.
 *
 * One notifier serves every session, so the same change arrives once per session and must only
 * alert once. The important part is that every weather is recorded, including the ones with no
 * alert configured: a guard that only remembered what it alerted on would stay pinned to that
 * weather and swallow its every return. That is what silenced Amber Moon after the first one.
 */
internal class WeatherAlertTracker {

    private var lastSeen: String = ""

    fun isNewWeather(weather: String, previousWeather: String): Boolean {
        if (weather.isBlank() || weather == previousWeather) return false
        if (weather == lastSeen) return false
        lastSeen = weather
        return true
    }
}
