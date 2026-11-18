package com.muzdai.qobuzbridge;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Turns the tracks the launcher sends (Qobuz catalogue fields, JSON) into the constructor arguments of Qobuz's
 * {@code com.qobuz.android.media.domain.model.player.state.queue.MediaQueueItem}, in the order and shape Qobuz's own
 * mapper uses (title and album with their version in brackets, duration in milliseconds). Pure Java, tested on the JVM.
 *
 * The 26 arguments: uuid, remoteId, trackId, trackingContextUuid, isrc, title, album, albumId, albumArt, artist,
 * artistId, playlistId, duration (long, ms), genreId, genreName, labelId, explicit, sample, preview, playable,
 * suggested, trackingSource (JSON), releaseDate, containerType, containerId, playlistTrackId.
 *
 * A track in the launcher's JSON: {"id":"64868955","title":"Come Together","version":"2019 Mix","isrc":"…",
 * "album":"Abbey Road","albumVersion":"…","albumId":"trrcz9pvaaz6b","albumArt":"https://…","artist":"The Beatles",
 * "artistId":"…","duration":259,"genreId":"119","genreName":"Rock","labelId":"…","explicit":false,
 * "releaseDate":"2019-09-27"}; only "id" is required.
 */
final class QueueItems {
    private QueueItems() { }

    static final int ARGS = 26;

    /** One argument list per track; a track without an id is skipped. */
    static List<Object[]> fromJson(String tracksJson, String query) throws JSONException {
        JSONArray tracks = new JSONArray(tracksJson);
        String context = UUID.randomUUID().toString();
        List<Object[]> out = new ArrayList<>();
        for (int i = 0; i < tracks.length(); i++) {
            JSONObject t = tracks.getJSONObject(i);
            String id = text(t, "id");
            if (id == null) continue;
            out.add(args(t, id, context, query, i + 1));
        }
        return out;
    }

    static Object[] args(JSONObject t, String id, String context, String query, int position) throws JSONException {
        String title = withVersion(text(t, "title"), text(t, "version"));
        String album = withVersion(text(t, "album"), text(t, "albumVersion"));
        long durationMs = t.optLong("duration", 0) * 1000L;
        return new Object[]{
                UUID.randomUUID().toString(),               // uuid
                null,                                       // remoteId
                id,                                         // trackId
                context,                                    // trackingContextUuid
                text(t, "isrc"),
                title,
                album,
                text(t, "albumId"),
                text(t, "albumArt"),
                text(t, "artist"),
                text(t, "artistId"),
                null,                                       // playlistId
                durationMs,
                text(t, "genreId"),
                text(t, "genreName"),
                text(t, "labelId"),
                t.optBoolean("explicit", false),
                false,                                      // sample
                false,                                      // preview
                true,                                       // playable
                false,                                      // suggested
                trackingSource(context, id, title, query, position),
                text(t, "releaseDate"),
                null,                                       // containerType
                null,                                       // containerId
                null,                                       // playlistTrackId
        };
    }

    /** As Qobuz records a track started from its search results (analytics; the player does not depend on it). */
    static String trackingSource(String context, String id, String title, String query, int position) throws JSONException {
        JSONObject path = new JSONObject()
                .put("category", "Search Results").put("name", "Tracks").put("level", "1").put("type", "Track")
                .put("value", query == null ? "" : query).put("position", position);
        return new JSONObject()
                .put("uuid", context).put("content_group_type", "Search Results").put("content_group_id", id)
                .put("content_group_name", title == null ? "" : title).put("trigger", "Track")
                .put("broadcast", "Native").put("tracking_path", path).toString();
    }

    static String withVersion(String name, String version) {
        if (name == null) return null;
        return version == null || version.isEmpty() ? name : name + " (" + version + ")";
    }

    private static String text(JSONObject o, String key) {
        if (!o.has(key) || o.isNull(key)) return null;
        String v = String.valueOf(o.opt(key)).trim();
        return v.isEmpty() ? null : v;
    }
}
