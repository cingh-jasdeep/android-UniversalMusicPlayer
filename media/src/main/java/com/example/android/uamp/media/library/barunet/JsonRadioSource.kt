package com.example.android.uamp.media.library.barunet

import android.content.Context
import android.net.Uri
import android.os.AsyncTask
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.android.uamp.media.R
import com.example.android.uamp.media.extensions.*
import com.example.android.uamp.media.library.AbstractMusicSource
import com.example.android.uamp.media.library.STATE_INITIALIZED
import com.example.android.uamp.media.library.STATE_INITIALIZING
import com.google.gson.Gson
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL

/**
 * Source of [MediaMetadataCompat] objects created from a basic JSON stream.
 *
 * The definition of the JSON is specified in the docs of [JsonRadioStation] in this file,
 * which is the object representation of it.
 */
class JsonRadioSource(context: Context, source: Uri) : AbstractMusicSource() {
    private var catalog: List<MediaMetadataCompat> = emptyList()

    init {
        state = STATE_INITIALIZING

        UpdateRadioCatalogTask(Glide.with(context)) { mediaItems ->
            catalog = mediaItems
            state = STATE_INITIALIZED
        }.execute(source)
    }

    override fun iterator(): Iterator<MediaMetadataCompat> = catalog.iterator()
}

/**
 * Task to connect to remote URIs and download/process JSON files that correspond to
 * [MediaMetadataCompat] objects.
 */
private class UpdateRadioCatalogTask(val glide: RequestManager,
                                     val listener: (List<MediaMetadataCompat>) -> Unit) :
        AsyncTask<Uri, Void, List<MediaMetadataCompat>>() {

    override fun doInBackground(vararg params: Uri): List<MediaMetadataCompat> {
        val mediaItems = ArrayList<MediaMetadataCompat>()

        params.forEach { catalogUri ->
            val musicCat = tryDownloadJson(catalogUri)

            // Get the base URI to fix up relative references later.
            val baseUri = catalogUri.toString().removeSuffix(catalogUri.lastPathSegment)

            mediaItems += musicCat.radio.map { song ->
                // The JSON may have paths that are relative to the source of the JSON
                // itself. We need to fix them up here to turn them into absolute paths.
                if (!song.image.startsWith(catalogUri.scheme)) {
                    song.image = baseUri + song.image
                }

                // Block on downloading artwork.
                val art = glide.applyDefaultRequestOptions(glideOptions)
                        .asBitmap()
                        .load(song.image)
                        .submit(NOTIFICATION_LARGE_ICON_SIZE, NOTIFICATION_LARGE_ICON_SIZE)
                        .get()

                MediaMetadataCompat.Builder()
                        .from(song)
                        .apply {
                            albumArt = art
                        }
                        .build()
            }.toList()
        }

        return mediaItems
    }

    override fun onPostExecute(mediaItems: List<MediaMetadataCompat>) {
        super.onPostExecute(mediaItems)
        listener(mediaItems)
    }

    /**
     * Attempts to download a catalog from a given Uri.
     *
     * @param catalogUri URI to attempt to download the catalog form.
     * @return The catalog downloaded, or an empty catalog if an error occurred.
     */
    private fun tryDownloadJson(catalogUri: Uri) =
            try {
                val catalogConn = URL(catalogUri.toString())
                val reader = BufferedReader(InputStreamReader(catalogConn.openStream()))
                Gson().fromJson<JsonRadioCatalog>(reader, JsonRadioCatalog::class.java)
            } catch (ioEx: IOException) {
                JsonRadioCatalog()
            }
}

/**
 * Extension method for [MediaMetadataCompat.Builder] to set the fields from
 * our JSON constructed object (to make the code a bit easier to see).
 */
fun MediaMetadataCompat.Builder.from(jsonRadioStation: JsonRadioStation): MediaMetadataCompat.Builder {

    id = jsonRadioStation.id
    title = jsonRadioStation.title
    genre = jsonRadioStation.genre
    mediaUri = jsonRadioStation.source
    albumArtUri = jsonRadioStation.image
    flag = MediaBrowserCompat.MediaItem.FLAG_PLAYABLE

    // To make things easier for *displaying* these, set the display properties as well.
    displayTitle = jsonRadioStation.title
    displayIconUri = jsonRadioStation.image

    // Add downloadStatus to force the creation of an "extras" bundle in the resulting
    // MediaMetadataCompat object. This is needed to send accurate metadata to the
    // media session during updates.
    downloadStatus = MediaDescriptionCompat.STATUS_NOT_DOWNLOADED

    // Allow it to be used in the typical builder style.
    return this
}

/**
 * Wrapper object for our JSON in order to be processed easily by GSON.
 */
class JsonRadioCatalog {
    var radio: List<JsonRadioStation> = ArrayList()
}

/**
 * An individual piece of radio included in our JSON Radio catalog.
 * The format from the server is as specified:
 * ```
        {"radio": [
        {
        "id": "radio_01",
        "title": // Title of the radio station
        "genre": // Primary genre of the radio station
        "source": // Path to the radio station stream
        "image": // Path to the art for the radio station, which may be relative
        "site": // Source of the radio station, if applicable
        }
        ]}
 * ```
 *
 * `image` can be provided in either relative or
 * absolute paths. For example:
 * ``
 *     "image" : "ode_to_joy.jpg"
 * ``
 *
 *`image` will be fetched relative to the path of the JSON file itself. This means
 * that if the JSON was at "https://www.example.com/json/music.json" then the image would be found
 * at "https://www.example.com/json/ode_to_joy.jpg".
 */
class JsonRadioStation {
    var id: String = ""
    var title: String = ""
    var genre: String = ""
    var source: String = ""
    var image: String = ""
    var site: String = ""
}

private const val NOTIFICATION_LARGE_ICON_SIZE = 144 // px

private val glideOptions = RequestOptions()
        .fallback(R.drawable.default_art)
        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
