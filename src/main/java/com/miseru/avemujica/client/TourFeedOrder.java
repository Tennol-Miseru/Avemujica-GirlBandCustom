package com.miseru.avemujica.client;

import com.miseru.avemujica.career.TourPost;
import java.util.ArrayList;
import java.util.List;

/** Sort/filter owner-authored feed metadata. The pinned entry is first only inside its matching tab. */
public final class TourFeedOrder {
    private TourFeedOrder() { }
    public static List<TourPost> order(List<TourPost> newestFirst, String pinnedKey, String filter) {
        String pin = pinnedKey == null ? "" : pinnedKey;
        List<TourPost> result = new ArrayList<>();
        TourPost pinned = null;
        for (TourPost post : newestFirst) {
            if ("TOUR".equals(filter) && post.manual()) continue;
            if ("CAMERA".equals(filter) && !post.manual()) continue;
            if (!pin.isEmpty() && post.feedKey().equals(pin)) pinned = post;
            else result.add(post);
        }
        if (pinned != null) result.add(0, pinned);
        return List.copyOf(result);
    }
}
