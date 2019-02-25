/*
 * Copyright 2018 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.uamp.media.library.barunet

import android.support.v4.media.MediaMetadataCompat
import com.example.android.uamp.media.library.MusicSource
import com.example.android.uamp.media.library.UAMP_BROWSABLE_ROOT

/**
 * Represents a tree of media that's used by [MusicService.onLoadChildren].
 *
 * [BrowseTreeRoot] maps a media id (see: [MediaMetadataCompat.METADATA_KEY_MEDIA_ID]) to
 * [UAMP_BROWSABLE_ROOT]
 *
 * For example, given the following conceptual tree:
 * root
 *  +-- Song_1
 *  +-- Song_2
 *  +-- Song_3
 *  ...
 *
 *  Requesting `browseTree["root"]` would return a list that of all "Songs" i.e. "Song_1",
 *  "Song_2" etc. Since those are leaf nodes, requesting `browseTree["Song_1"]`
 *  would return null (there aren't any children of it).
 */
class BrowseTreeRoot(musicSource: MusicSource) {
    private val mediaIdToChildren = mutableMapOf<String, MutableList<MediaMetadataCompat>>()

    /**
     * In this example, there's a single root node (identified by the constant
     * [UAMP_BROWSABLE_ROOT]). The root's children are each song included in the
     * [MusicSource], and the children of each album are the songs on that album.
     * (See [BrowseTreeRoot.buildAlbumRoot] for more details.)
     *
     */
    init {
        musicSource.forEach { mediaItem ->
            // Ensure the root node exists and add this mediaItem to the list.
            val rootList = mediaIdToChildren[UAMP_BROWSABLE_ROOT] ?: mutableListOf()
            rootList += mediaItem
            mediaIdToChildren[UAMP_BROWSABLE_ROOT] = rootList
        }
    }

    /**
     * Provide access to the list of children with the `get` operator.
     * i.e.: `browseTree\[UAMP_BROWSABLE_ROOT\]`
     */
    operator fun get(mediaId: String) = mediaIdToChildren[mediaId]
}