# One-off helper that appended the Music tab texts to both language files (kept for reference; safe to re-run).
import os
import re

S = [
    ("music_title", "Music", "음악"),
    ("music_tab_home", "Home", "홈"),
    ("music_tab_search", "Search", "검색"),
    ("music_tab_library", "Your Library", "내 라이브러리"),
    ("music_good_morning", "Good morning", "좋은 아침이에요"),
    ("music_good_afternoon", "Good afternoon", "좋은 오후예요"),
    ("music_good_evening", "Good evening", "좋은 저녁이에요"),
    ("music_recently_played", "Recently played", "최근 재생"),
    ("music_top_songs", "Top songs right now", "지금 인기 있는 곡"),
    ("music_popular_albums", "Popular albums", "인기 앨범"),
    ("music_popular_artists", "Popular artists", "인기 아티스트"),
    ("music_popular_playlists", "Popular playlists", "인기 플레이리스트"),
    ("music_browse_genres", "Browse all", "장르별로 둘러보기"),
    ("music_loading_failed", "Can't load music. Check your connection.", "음악을 불러오지 못했어요. 인터넷 연결을 확인해 주세요."),
    ("music_retry", "Try again", "다시 시도"),
    ("music_search_hint", "What do you want to listen to?", "어떤 음악을 듣고 싶나요?"),
    ("music_search_tip", "Search songs, artists, albums and playlists", "노래, 아티스트, 앨범, 플레이리스트를 검색해 보세요"),
    ("music_recent_searches", "Recent searches", "최근 검색"),
    ("music_clear_all", "Clear all", "모두 지우기"),
    ("music_filter_all", "All", "전체"),
    ("music_filter_songs", "Songs", "노래"),
    ("music_filter_albums", "Albums", "앨범"),
    ("music_filter_artists", "Artists", "아티스트"),
    ("music_filter_playlists", "Playlists", "플레이리스트"),
    ("music_top_result", "Top result", "인기 결과"),
    ("music_no_results", 'No results for "%1$s"', '"%1$s"에 대한 결과가 없어요'),
    ("music_liked_songs", "Liked Songs", "좋아요 표시한 곡"),
    ("music_songs_count", "%1$d songs", "%1$d곡"),
    ("music_one_song", "1 song", "1곡"),
    ("music_on_this_device", "On this device", "이 기기의 음악"),
    ("music_create_playlist", "Create playlist", "플레이리스트 만들기"),
    ("music_playlist_name", "Playlist name", "플레이리스트 이름"),
    ("music_my_playlist", "My playlist #%1$d", "내 플레이리스트 #%1$d"),
    ("music_create", "Create", "만들기"),
    ("music_save", "Save", "저장"),
    ("music_library_empty", "Nothing here yet. Like a song, follow an artist or create a playlist.", "아직 비어 있어요. 곡에 좋아요를 누르거나 아티스트를 팔로우하거나 플레이리스트를 만들어 보세요."),
    ("music_playlist_by", "Playlist • %1$s", "플레이리스트 • %1$s"),
    ("music_album_by", "Album • %1$s", "앨범 • %1$s"),
    ("music_artist_label", "Artist", "아티스트"),
    ("music_play", "Play", "재생"),
    ("music_pause", "Pause", "일시정지"),
    ("music_shuffle", "Shuffle", "셔플"),
    ("music_shuffle_play", "Shuffle play", "셔플 재생"),
    ("music_popular", "Popular", "인기곡"),
    ("music_albums", "Albums", "앨범"),
    ("music_fans_count", "%1$s fans", "팬 %1$s명"),
    ("music_related_artists", "Fans also like", "비슷한 아티스트"),
    ("music_follow", "Follow", "팔로우"),
    ("music_following", "Following", "팔로잉"),
    ("music_start_radio", "Artist radio", "아티스트 라디오"),
    ("music_save_to_library", "Save to library", "라이브러리에 저장"),
    ("music_remove_from_library", "Remove from library", "라이브러리에서 삭제"),
    ("music_saved_to_library", "Saved to your library", "라이브러리에 저장했어요"),
    ("music_removed_from_library", "Removed from your library", "라이브러리에서 삭제했어요"),
    ("music_show_more", "Show more", "더 보기"),
    ("music_show_less", "Show less", "접기"),
    ("music_year_songs", "%1$s • %2$d songs", "%1$s • %2$d곡"),
    ("music_rename", "Rename", "이름 바꾸기"),
    ("music_delete_playlist", "Delete playlist", "플레이리스트 삭제"),
    ("music_delete_confirm", 'Delete "%1$s"? This can\'t be undone.', '"%1$s"을(를) 삭제할까요? 되돌릴 수 없어요.'),
    ("music_delete", "Delete", "삭제"),
    ("music_empty_playlist", "This playlist is empty. Add songs from the ⋮ menu of any song.", "비어 있는 플레이리스트예요. 아무 곡의 ⋮ 메뉴에서 추가해 보세요."),
    ("music_find_songs", "Find songs", "곡 찾아보기"),
    ("music_grant_access", "Allow access", "접근 허용"),
    ("music_local_permission", "Allow access to the music on this phone to play it here, in full length.", "이 폰에 있는 음악을 여기서 전곡 재생하려면 접근을 허용해 주세요."),
    ("music_local_empty", "No music files found on this phone.", "이 폰에서 음악 파일을 찾지 못했어요."),
    ("music_local_search", "Search on this device", "이 기기에서 검색"),
    ("music_unknown_artist", "Unknown artist", "알 수 없는 아티스트"),
    ("music_add_to_liked", "Add to Liked Songs", "좋아요 표시한 곡에 추가"),
    ("music_remove_from_liked", "Remove from Liked Songs", "좋아요 표시한 곡에서 삭제"),
    ("music_added_to_liked", "Added to Liked Songs", "좋아요 표시한 곡에 추가했어요"),
    ("music_removed_from_liked", "Removed from Liked Songs", "좋아요 표시한 곡에서 삭제했어요"),
    ("music_add_to_playlist", "Add to playlist", "플레이리스트에 추가"),
    ("music_added_to_playlist", "Added to %1$s", "%1$s에 추가했어요"),
    ("music_already_in_playlist", "Already in %1$s", "이미 %1$s에 있어요"),
    ("music_new_playlist", "New playlist", "새 플레이리스트"),
    ("music_play_next", "Play next", "다음에 재생"),
    ("music_add_to_queue", "Add to queue", "대기열에 추가"),
    ("music_go_to_artist", "Go to artist", "아티스트로 이동"),
    ("music_go_to_album", "Go to album", "앨범으로 이동"),
    ("music_share", "Share", "공유"),
    ("music_open_in_spotify", "Search on Spotify", "Spotify에서 검색"),
    ("music_remove_from_playlist", "Remove from this playlist", "이 플레이리스트에서 삭제"),
    ("music_will_play_next", "Will play next", "다음에 재생할게요"),
    ("music_added_to_queue", "Added to queue", "대기열에 추가했어요"),
    ("music_cant_play", "Can't play this song", "이 곡을 재생할 수 없어요"),
    ("music_now_playing", "Now playing", "지금 재생 중"),
    ("music_queue", "Queue", "대기열"),
    ("music_next_up", "Next up", "다음 곡"),
    ("music_clear_queue", "Clear queue", "대기열 비우기"),
    ("music_queue_empty", "Nothing is queued after this song.", "이 곡 다음으로 대기 중인 곡이 없어요."),
    ("music_sleep_timer", "Sleep timer", "수면 타이머"),
    ("music_sleep_off", "Turn off timer", "타이머 끄기"),
    ("music_sleep_minutes", "%1$d minutes", "%1$d분"),
    ("music_sleep_end_of_track", "End of this song", "이 곡이 끝나면"),
    ("music_sleep_after_track_active", "Stops after this song", "이 곡이 끝나면 멈춰요"),
    ("music_sleep_active", "Stops in %1$s", "%1$s 뒤에 멈춰요"),
    ("music_preview_note", "30-second preview", "30초 미리듣기"),
    ("music_preview_note_long", "Online songs play a 30-second preview (that is all the free catalog allows). Songs on your phone play in full.", "온라인 곡은 30초 미리듣기로 재생돼요(무료 카탈로그가 허용하는 범위예요). 폰에 있는 곡은 전곡 재생돼요."),
    ("music_back", "Back", "뒤로"),
    ("music_more", "More", "더 보기"),
    ("music_close", "Close", "닫기"),
    ("music_next", "Next", "다음 곡"),
    ("music_previous", "Previous", "이전 곡"),
    ("music_like", "Like", "좋아요"),
    ("music_unlike", "Remove like", "좋아요 취소"),
    ("music_repeat_off", "Repeat off", "반복 끔"),
    ("music_repeat_all", "Repeat all", "전체 반복"),
    ("music_repeat_one", "Repeat this song", "한 곡 반복"),
    ("music_shuffle_on", "Shuffle on", "셔플 켜짐"),
    ("music_shuffle_off", "Shuffle off", "셔플 꺼짐"),
    ("music_settings_title", "Music", "음악"),
    ("music_clear_data", "Clear music data", "음악 데이터 지우기"),
    ("music_clear_data_desc", "Liked songs, playlists, saved albums, followed artists and history on this phone", "이 폰의 좋아요 표시한 곡, 플레이리스트, 저장한 앨범, 팔로우한 아티스트, 기록"),
    ("music_clear_data_confirm", "Erase everything the Music tab keeps on this phone? This can't be undone.", "음악 탭이 이 폰에 저장한 모든 것을 지울까요? 되돌릴 수 없어요."),
    ("music_data_cleared", "Music data cleared", "음악 데이터를 지웠어요"),
    ("music_stop_playback", "Stop playback", "재생 끝내기"),
    ("music_playing_from", "Playing from %1$s", "%1$s에서 재생 중"),
    ("music_empty_recent", "Songs you play will show up here.", "재생한 곡이 여기에 나타나요."),
]


def escape(text):
    # Android string resources: & < > escaped, apostrophe and double quote with a backslash.
    return (text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("'", "\\'").replace('"', '\\"'))


def main():
    root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res")
    for lang, idx in (("values", 1), ("values-ko", 2)):
        path = os.path.join(root, lang, "strings_manual.xml")
        s = open(path, encoding="utf-8", newline="").read()
        crlf = "\r\n" in s
        s = s.replace("\r\n", "\n")
        if "music_title" not in s:
            block = "".join('    <string name="%s">%s</string>\n' % (row[0], escape(row[idx])) for row in S)
            s = s.replace("</resources>", block + "</resources>")
        if crlf:
            s = s.replace("\n", "\r\n")
        open(path, "w", encoding="utf-8", newline="").write(s)
    for lang, value in (("values", "Apps"), ("values-ko", "앱")):
        path = os.path.join(root, lang, "strings.xml")
        s = open(path, encoding="utf-8", newline="").read()
        s = re.sub(r'(<string name="t_web_games">)[^<]*(</string>)', lambda m: m.group(1) + value + m.group(2), s)
        open(path, "w", encoding="utf-8", newline="").write(s)
    print(len(S), "strings")


main()
