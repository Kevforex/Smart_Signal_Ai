package com.example

import com.example.data.remote.BiquoteApiException
import com.example.data.remote.BiquoteJsonParser
import com.example.data.remote.NetworkModule
import com.example.data.repository.MarketRepository
import com.example.domain.analysis.TechnicalIndicators
import com.example.domain.model.Timeframe
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BiquoteJsonParserTest {

    @Test
    fun `parseOhlcResponse reads object bars and filters open candles`() {
        val payload = """
            {
              "symbol": "EURUSD",
              "interval": "15m",
              "bars": [
                {"openTime":"2026-11-20T16:45:00Z","open":1.09856,"high":1.09859,"low":1.09822,"close":1.09823,"volume":0,"tickVolume":308,"isOpen":true},
                {"openTime":"2026-11-20T16:30:00Z","open":1.09809,"high":1.09867,"low":1.09794,"close":1.09853,"volume":0,"tickVolume":1012,"isOpen":false},
                {"openTime":"2026-11-20T16:15:00Z","open":1.09800,"high":1.09859,"low":1.09795,"close":1.09809,"volume":0,"tickVolume":1231,"isOpen":false}
              ]
            }
        """.trimIndent()

        val all = BiquoteJsonParser.parseOhlcResponse(payload, "EURUSD")
        assertEquals(3, all.size)
        // Sorted ascending chronologically
        assertEquals("2026-11-20T16:15:00Z", all.first().openTimeIso)
        assertTrue(all.last().isOpen)

        val closed = all.filter { !it.isOpen }
        assertEquals(2, closed.size)
        assertFalse(closed.any { it.isOpen })
        assertEquals(1012L, closed.last().tickVolume)
    }

    @Test
    fun `parseOhlcResponse reads nested data bars format`() {
        val payload = """
            {
              "data": {
                "bars": [
                  {"openTime":"2026-11-20T15:00:00Z","open":1.09815,"high":1.09974,"low":1.09778,"close":1.09829,"tickVolume":6200,"isOpen":false}
                ]
              }
            }
        """.trimIndent()

        val bars = BiquoteJsonParser.parseOhlcResponse(payload, "EURUSD")
        assertEquals(1, bars.size)
        assertEquals(6200L, bars.first().tickVolume)
    }

    @Test
    fun `parseLatestQuote reads live object keyed by symbol without BEGIN_ARRAY error`() {
        val liveObjectPayload = """
            {
              "EURUSD": {
                "symbol": "EURUSD",
                "bid": 1.09821,
                "ask": 1.09827,
                "timestamp": "2026-11-20T16:50:27Z",
                "high": 1.09999,
                "low": 1.09568,
                "direction": "FLAT",
                "dayDiffPercent": 0.0984,
                "description": "Euro vs US Dollar",
                "spread": 0.00006,
                "mid": 1.09824,
                "stale": false,
                "quoteAgeSeconds": 0,
                "marketState": "open"
              }
            }
        """.trimIndent()

        val quote = BiquoteJsonParser.parseLatestQuote(liveObjectPayload, "EURUSD")
        assertEquals("EURUSD", quote.symbol)
        assertEquals(1.09821, quote.bid, 0.000001)
        assertEquals("open", quote.marketState)
        assertFalse(quote.stale)
    }

    @Test
    fun `parseLatestQuote also supports array format`() {
        val arrayPayload = """
            [
              {"symbol":"EURUSD","bid":1.09800,"ask":1.09806,"stale":false,"quoteAgeSeconds":1,"marketState":"open","timestamp":"2026-11-20T16:50:00Z"}
            ]
        """.trimIndent()

        val quote = BiquoteJsonParser.parseLatestQuote(arrayPayload, "EURUSD")
        assertEquals(1.09800, quote.bid, 0.000001)
    }

    @Test(expected = BiquoteApiException::class)
    fun `parseOhlcResponse throws explicit error on empty bars`() {
        BiquoteJsonParser.parseOhlcResponse("""{"symbol":"EURUSD","bars":[]}""", "EURUSD")
    }

    @Test(expected = BiquoteApiException::class)
    fun `parseOhlcResponse throws explicit error on malformed JSON`() {
        BiquoteJsonParser.parseOhlcResponse("{not valid json", "EURUSD")
    }

    @Test(expected = BiquoteApiException::class)
    fun `parseOhlcResponse throws explicit error on blank response`() {
        BiquoteJsonParser.parseOhlcResponse("   ", "EURUSD")
    }

    @Test
    fun `parseOhlcResponse drops invalid high-lt-low bars and deduplicates timestamps`() {
        val payload = """
            {
              "symbol": "EURUSD",
              "bars": [
                {"openTime":"2026-11-20T16:30:00Z","open":1.09809,"high":1.09867,"low":1.09794,"close":1.09853,"tickVolume":100,"isOpen":false},
                {"openTime":"2026-11-20T16:15:00Z","open":1.09800,"high":1.09750,"low":1.09810,"close":1.09805,"tickVolume":50,"isOpen":false},
                {"openTime":"2026-11-20T16:30:00Z","open":1.09809,"high":1.09867,"low":1.09794,"close":1.09853,"tickVolume":100,"isOpen":false},
                {"openTime":"2026-11-20T16:00:00Z","open":1.09790,"high":1.09820,"low":1.09780,"close":1.09800,"tickVolume":90,"isOpen":false}
              ]
            }
        """.trimIndent()

        val bars = BiquoteJsonParser.parseOhlcResponse(payload, "EURUSD")
        assertEquals(2, bars.size)
        assertEquals("2026-11-20T16:00:00Z", bars[0].openTimeIso)
        assertEquals("2026-11-20T16:30:00Z", bars[1].openTimeIso)
    }

    @Test(expected = BiquoteApiException::class)
    fun `parseLatestQuote rejects non-positive bid`() {
        BiquoteJsonParser.parseLatestQuote("""{"EURUSD":{"symbol":"EURUSD","bid":0,"ask":1.09}}""", "EURUSD")
    }

    @Test
    fun `live Biquote M15 limit 192 and H1 limit 168 and latest connections succeed and feed indicators`() = runBlocking {
        val api = NetworkModule.biquoteApiService

        val latestResp = api.getLatestQuotes(listOf("EURUSD"))
        assertTrue("HTTP latest should succeed, got ${latestResp.code()}", latestResp.isSuccessful)
        val latestBody = latestResp.body()?.string()
        val quote = BiquoteJsonParser.parseLatestQuote(latestBody, "EURUSD")
        assertEquals("EURUSD", quote.symbol)
        assertTrue(quote.bid > 0.5)

        val m15Resp = api.getOhlc("EURUSD", Timeframe.M15.apiValue, MarketRepository.M15_LIMIT)
        assertTrue("HTTP M15 should succeed, got ${m15Resp.code()}", m15Resp.isSuccessful)
        val m15Candles = BiquoteJsonParser.parseOhlcResponse(m15Resp.body()?.string(), "EURUSD")
        val m15Closed = m15Candles.filter { !it.isOpen }
        assertTrue("Expected >= 120 closed M15 bars, got ${m15Closed.size}", m15Closed.size >= 120)

        val h1Resp = api.getOhlc("EURUSD", Timeframe.H1.apiValue, MarketRepository.H1_LIMIT)
        assertTrue("HTTP H1 should succeed, got ${h1Resp.code()}", h1Resp.isSuccessful)
        val h1Candles = BiquoteJsonParser.parseOhlcResponse(h1Resp.body()?.string(), "EURUSD")
        val h1Closed = h1Candles.filter { !it.isOpen }
        assertTrue("Expected >= 120 closed H1 bars, got ${h1Closed.size}", h1Closed.size >= 120)

        val m15Snap = TechnicalIndicators.buildSnapshot("EURUSD", Timeframe.M15, m15Closed)
        val h1Snap = TechnicalIndicators.buildSnapshot("EURUSD", Timeframe.H1, h1Closed)

        assertNotNull("EMA 50 M15 must be computed", m15Snap.ema50)
        assertNotNull("EMA 200 M15 must be computed", m15Snap.ema200)
        assertNotNull("RSI 14 M15 must be computed", m15Snap.rsi14)
        assertNotNull("ATR 14 M15 must be computed", m15Snap.atr14)
        assertNotNull("EMA 50 H1 must be computed", h1Snap.ema50)
        assertNotNull("EMA 200 H1 must be computed", h1Snap.ema200)
    }
}
