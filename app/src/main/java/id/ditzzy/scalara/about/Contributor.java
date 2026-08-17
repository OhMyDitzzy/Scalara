package id.ditzzy.scalara.about;

import com.google.gson.annotations.SerializedName;

/**
 * One entry from GitHub's {@code GET /repos/{owner}/{repo}/contributors}
 * response. Field names are annotated with {@link SerializedName} to match
 * GitHub's JSON exactly (snake_case where GitHub uses it) while keeping this
 * class's own field names idiomatic Java.
 *
 * <p>Only the fields {@link AboutActivity}'s contributor list actually
 * displays are declared here — GitHub's response includes several more
 * (type, site_admin, events_url, and so on) that Gson simply ignores since
 * they have no matching field, rather than this class needing to model the
 * entire response shape.
 */
public final class Contributor {

    @SerializedName("login")
    private String login;

    @SerializedName("avatar_url")
    private String avatarUrl;

    @SerializedName("html_url")
    private String profileUrl;

    @SerializedName("contributions")
    private int contributions;

    public String getLogin() {
        return login;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getProfileUrl() {
        return profileUrl;
    }

    public int getContributions() {
        return contributions;
    }
}