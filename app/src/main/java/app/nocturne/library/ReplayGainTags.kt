package app.nocturne.library

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.MetadataRetriever
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import app.nocturne.data.Track
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** ReplayGain values found in a file's tags, converted to the -18 LUFS reference. Pure parsing, unit-tested. */
object ReplayGainTags {
    data class Gain(val gainDb: Float, val peak: Float?)

    private val NUMBER = Regex("[-+−]?\\s*\\d+(?:[.,]\\d+)?")

    /** "-6.50 dB", "+3.1", "−2,5 dB" -> dB; null when there is no number. */
    fun parseDb(text: String): Float? =
        NUMBER.find(text)?.value?.replace('−', '-')?.replace(" ", "")?.replace(',', '.')?.toFloatOrNull()

    fun parsePeak(text: String): Float? = parseDb(text)?.takeIf { it > 0f }

    /**
     * [tags] are key/value pairs from any tag format (ID3 TXXX, Vorbis comments, MP4 freeform); keys are matched
     * case-insensitively. Track gain wins over album gain; Opus R128_* gains (Q7.8 dB relative to -23 LUFS) are
     * shifted to the -18 LUFS reference. Null when the file has no usable gain tag.
     */
    fun fromTags(tags: List<Pair<String, String>>): Gain? {
        val map = HashMap<String, String>()
        for ((k, v) in tags) map.putIfAbsent(k.trim().uppercase(), v)
        val peak = map["REPLAYGAIN_TRACK_PEAK"]?.let(::parsePeak)
        map["REPLAYGAIN_TRACK_GAIN"]?.let(::parseDb)?.let { return Gain(it, peak) }
        map["R128_TRACK_GAIN"]?.trim()?.toIntOrNull()?.let { return Gain(it / 256f + R128_TO_REFERENCE_DB, peak) }
        map["REPLAYGAIN_ALBUM_GAIN"]?.let(::parseDb)?.let { return Gain(it, peak) }
        map["R128_ALBUM_GAIN"]?.trim()?.toIntOrNull()?.let { return Gain(it / 256f + R128_TO_REFERENCE_DB, peak) }
        return null
    }

    /** EBU R128 targets -23 LUFS, ReplayGain 2 -18 LUFS. */
    private const val R128_TO_REFERENCE_DB = 5f

    /** Flattens Media3 metadata entries (all formats' tag types) into key/value pairs. */
    @UnstableApi
    fun tagsOf(metadata: Metadata?): List<Pair<String, String>> {
        if (metadata == null) return emptyList()
        val out = ArrayList<Pair<String, String>>()
        for (i in 0 until metadata.length()) {
            when (val e = metadata.get(i)) {
                is TextInformationFrame -> if (e.id == "TXXX" && e.values.isNotEmpty()) out.add((e.description ?: "") to e.values[0])
                is VorbisComment -> out.add(e.key to e.value)
                is InternalFrame -> out.add(e.description to e.text)
            }
        }
        return out
    }
}

/** Reads ReplayGain tags through the player's own data sources, so SMB and device files both work. */
@UnstableApi
class ReplayGainReader(private val context: Context, private val smb: SmbProviders) {
    /** Null when the file carries no ReplayGain/R128 tag (or its format exposes none). Blocking work runs on IO. */
    suspend fun read(track: Track): ReplayGainTags.Gain? = withContext(Dispatchers.IO) {
        val factory = DefaultMediaSourceFactory(RoutingDataSource.Factory(context, smb), DefaultExtractorsFactory())
        val groups = MetadataRetriever.retrieveMetadata(factory, MediaItem.fromUri(track.uri)).get(30, TimeUnit.SECONDS)
        val tags = ArrayList<Pair<String, String>>()
        for (g in 0 until groups.length) {
            val group = groups.get(g)
            for (f in 0 until group.length) tags.addAll(ReplayGainTags.tagsOf(group.getFormat(f).metadata))
        }
        ReplayGainTags.fromTags(tags)
    }
}
