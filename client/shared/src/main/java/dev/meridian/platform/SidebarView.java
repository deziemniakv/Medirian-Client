package dev.meridian.platform;

/**
 * Snapshot of the sidebar scoreboard. Text values are native rich-text objects (they keep the
 * server's colours) and are drawn with {@link dev.meridian.render.Gfx#richText}.
 */
public interface SidebarView {

    Object title();

    /** Number of lines, at most 15, top to bottom. */
    int lineCount();

    Object line(int index);

    /** Native rich text of the score shown on the right, or null when the server hides it. */
    Object score(int index);
}
