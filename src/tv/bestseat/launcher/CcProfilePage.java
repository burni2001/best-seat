package tv.bestseat.launcher;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Profilseiten im Kontrollzentrum (W21): Profile wählen, anlegen, bearbeiten und entfernen. */
final class CcProfilePage {

    private final ControlCenterActivity cc;
    /** Langes OK auf einem anderen Profil: nach dem Neuaufbau gleich bearbeiten. */
    private boolean editAfterSwitch;

    CcProfilePage(ControlCenterActivity cc) {
        this.cc = cc;
    }

    boolean isProfileSub() {
        Object t = cc.body.getTag();
        return t != null && String.valueOf(t).startsWith("profile_");
    }

    /** Profile wie tvOS (W11): alle Profile als Kreise, Haken am aktiven, „+“ zum Hinzufügen. */
    void showProfile() {
        // Profilseite genauso groß wie die Kachelseite (Höhe merken, solange sie noch zu sehen ist).
        if (cc.body.getTag() == null && cc.panel.getHeight() > 0) cc.mainPanelHeight = cc.panel.getHeight();
        cc.setPanelHeight(cc.mainPanelHeight);
        cc.body.removeAllViews();
        cc.body.setTag("profile");
        cc.pill.setSelected("profile");
        java.util.List<Profiles.Profile> list = Profiles.all(cc);
        String currentId = Profiles.currentId(cc);
        // tvOS: große Kreise, zwei je Reihe, Name darunter.
        LinearLayout[] rowRef = new LinearLayout[1];
        java.util.function.Supplier<LinearLayout> nextRow = () -> {
            if (rowRef[0] == null || rowRef[0].getChildCount() == 2) {
                LinearLayout row = new LinearLayout(cc);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_HORIZONTAL);
                // Platzhalter rechts, damit eine einzelne Zelle unter der linken steht.
                row.setTag("profiles");
                row.setClipChildren(false);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.topMargin = Ui.dp(cc, cc.body.getChildCount() == 0 ? 6 : 14);
                cc.body.addView(row, rlp);
                rowRef[0] = row;
            }
            return rowRef[0];
        };
        View focus = null;
        for (Profiles.Profile pr : list) {
            boolean active = pr.id.equals(currentId);
            View cell = profileCell(nextRow.get(), pr, pr.name, active);
            // OK wechselt, langes OK bearbeitet (wie tvOS; ein anderes Profil wird dafür erst aktiv).
            cell.setOnClickListener(v -> {
                if (!active) switchProfile(pr.id);
            });
            cell.setOnLongClickListener(v -> {
                if (active) {
                    showEditProfile();
                } else {
                    Profiles.switchTo(cc, pr.id);
                    cc.profileSwitched = true;
                    cc.pill.refreshAvatar();
                    editAfterSwitch = true;
                    showProfile();
                }
                return true;
            });
            if (active) focus = cell;
        }
        if (list.size() < Profiles.MAX) {
            View add = profileCell(nextRow.get(), null, "Add Profile", false);
            add.setOnClickListener(v -> askProfileName("New profile", "", name -> {
                String id = Profiles.add(cc, name);
                switchProfile(id);
            }));
        }
        // Letzte Reihe mit nur einer Zelle: unsichtbarer Platzhalter, damit sie links bündig steht.
        if (rowRef[0] != null && rowRef[0].getChildCount() == 1) {
            rowRef[0].addView(new View(cc), new LinearLayout.LayoutParams(Ui.dp(cc, 120), 1));
        }
        if (focus != null) focus.requestFocus();
        if (editAfterSwitch) {
            editAfterSwitch = false;
            showEditProfile();
        }
    }

    private static final int PROFILE_DP = 76;

    /** Ein Profil als großer Kreis mit Namen darunter, aktives mit grünem Haken; profile null = „+“. */
    private View profileCell(LinearLayout row, Profiles.Profile profile, String label, boolean active) {
        LinearLayout cell = new LinearLayout(cc);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setFocusable(true);
        cell.setClickable(true);
        cell.setClipChildren(false);
        int size = Ui.dp(cc, PROFILE_DP);
        FrameLayout holder = new FrameLayout(cc);
        holder.setClipChildren(false);
        TextView circle = new TextView(cc);
        circle.setGravity(Gravity.CENTER);
        circle.setIncludeFontPadding(false);
        holder.addView(circle, new FrameLayout.LayoutParams(size, size));
        if (active) {
            // Grüner Haken unten rechts mit Rand in Kastenfarbe.
            TextView badge = new TextView(cc);
            badge.setText("\u2713");
            badge.setGravity(Gravity.CENTER);
            badge.setIncludeFontPadding(false);
            badge.setTextColor(Color.WHITE);
            badge.setTypeface(Fonts.get(badge.getContext(), Fonts.BOLD));
            badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(Color.rgb(52, 199, 89));
            g.setStroke(Ui.dp(cc, 2), Ui.light ? Color.rgb(236, 236, 240) : Color.rgb(44, 44, 48));
            badge.setBackground(g);
            int b = Ui.dp(cc, 24);
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(b, b, Gravity.END | Gravity.BOTTOM);
            holder.addView(badge, blp);
        }
        cell.addView(holder, new LinearLayout.LayoutParams(size, size));
        TextView text = Ui.text(cc, label, 12, active ? Ui.TEXT : Ui.TEXT_DIM, active);
        text.setGravity(Gravity.CENTER);
        text.setSingleLine(true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Ui.dp(cc, 8);
        cell.addView(text, tlp);
        Runnable style = () -> {
            boolean has = cell.isFocused();
            if (profile == null) {
                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.OVAL);
                bg.setColor(Ui.light ? Color.rgb(205, 208, 216) : Color.rgb(72, 74, 82));
                if (has) bg.setStroke(Ui.dp(cc, 2), Ui.light ? Color.rgb(22, 24, 28) : Color.WHITE);
                circle.setBackground(bg);
                circle.setText("+");
                circle.setTypeface(Fonts.get(circle.getContext(), Fonts.REGULAR));
                circle.setTextColor(Ui.TEXT);
                circle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
            } else {
                Avatars.style(cc, circle, profile.picture(), profile.color, has, profile.name, PROFILE_DP);
            }
        };
        style.run();
        cell.setOnFocusChangeListener((v, has) -> {
            style.run();
            holder.animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f).setDuration(Ui.ms(140)).start();
            text.setTextColor(has || active ? Ui.TEXT : Ui.TEXT_DIM);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(cc, 120),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        row.addView(cell, lp);
        return cell;
    }

    /** Anderes Profil: Startseite mit dessen Layout neu starten. */
    private void switchProfile(String id) {
        Profiles.switchTo(cc, id);
        cc.startActivity(new Intent(cc, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        cc.finish();
        Ui.transition(cc, 0, 0);
    }

    /** Aktuelles Profil bearbeiten: Profilbild, Name, Entfernen. */
    private void showEditProfile() {
        cc.setPanelHeight(0);
        cc.body.removeAllViews();
        cc.body.setTag("profile_edit");
        TextView name = Ui.text(cc, Prefs.userName(cc), 15, Ui.TEXT, true);
        name.setPadding(Ui.dp(cc, 4), 0, 0, Ui.dp(cc, 2));
        cc.body.addView(name);
        TextView hint = Ui.dimText(cc, "Choose your profile picture");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        hint.setPadding(Ui.dp(cc, 4), 0, 0, Ui.dp(cc, 10));
        cc.body.addView(hint);
        Profiles.Profile me = Profiles.current(cc);
        String current = me.picture();
        // Kleine Auswahl wie bisher (Anfangsbuchstabe und Tiere), das aktuelle Bild immer dabei,
        // am Ende „…“ für alle Emojis im Overlay.
        java.util.List<String> quick = new java.util.ArrayList<>();
        for (int i = 0; i < 9; i++) quick.add(Prefs.AVATARS[i]);
        if (!quick.contains(current)) quick.set(8, current);
        View focus = null;
        LinearLayout row = null;
        int size = Ui.dp(cc, 40);
        for (int i = 0; i <= quick.size(); i++) {
            if (i % 5 == 0) row = avatarRow();
            TextView a = avatarCell(row, i % 5 > 0);
            if (i == quick.size()) {
                // Alle Emojis
                Runnable st = () -> {
                    GradientDrawable bg = new GradientDrawable();
                    bg.setShape(GradientDrawable.OVAL);
                    bg.setColor(Ui.light ? Color.rgb(205, 208, 216) : Color.rgb(60, 62, 70));
                    if (a.isFocused()) bg.setStroke(Ui.dp(cc, 2), Ui.light ? Color.rgb(22, 24, 28) : Color.WHITE);
                    a.setBackground(bg);
                };
                a.setText("\u2026");
                a.setTextColor(Ui.TEXT);
                a.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
                st.run();
                a.setOnFocusChangeListener((v, has) -> {
                    st.run();
                    v.animate().scaleX(has ? 1.15f : 1f).scaleY(has ? 1.15f : 1f).setDuration(Ui.ms(140)).start();
                });
                a.setOnClickListener(v -> showAllEmojis(current));
                continue;
            }
            final String emoji = quick.get(i);
            Avatars.style(cc, a, emoji, me.color, false, me.name, 40);
            a.setOnFocusChangeListener((v, has) -> {
                Avatars.style(cc, a, emoji, Profiles.current(cc).color, has, me.name, 40);
                v.animate().scaleX(has ? 1.15f : 1f).scaleY(has ? 1.15f : 1f).setDuration(Ui.ms(140)).start();
            });
            a.setOnClickListener(v -> {
                Profiles.setPicture(cc, me.id, emoji, null);
                cc.pill.refreshAvatar();
                showProfile();
            });
            if (emoji.equals(current) && focus == null) focus = a;
        }
        // Hintergrundfarbe: das eigene Bild auf jeder Farbe, die gewählte mit dünnem Ring.
        TextView colorTitle = Ui.dimText(cc, "Background");
        colorTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        colorTitle.setPadding(Ui.dp(cc, 4), Ui.dp(cc, 4), 0, Ui.dp(cc, 8));
        cc.body.addView(colorTitle);
        for (int i = 0; i < Avatars.colorCount(); i++) {
            if (i % 5 == 0) row = avatarRow();
            TextView a = avatarCell(row, i % 5 > 0);
            final int color = i;
            boolean chosen = color == me.color || (me.color < 0 && color == 0 && !current.isEmpty());
            Runnable st = () -> {
                Avatars.style(cc, a, current, color, a.isFocused(), me.name, 40);
                if (chosen && !a.isFocused() && a.getBackground() instanceof GradientDrawable) {
                    ((GradientDrawable) a.getBackground()).setStroke(Ui.dp(cc, 2), Color.argb(150, 255, 255, 255));
                }
            };
            st.run();
            a.setOnFocusChangeListener((v, has) -> {
                st.run();
                v.animate().scaleX(has ? 1.15f : 1f).scaleY(has ? 1.15f : 1f).setDuration(Ui.ms(140)).start();
            });
            a.setOnClickListener(v -> {
                Profiles.setPicture(cc, me.id, null, color);
                cc.pill.refreshAvatar();
                showEditProfile();
                focusColor(color);
            });
        }
        cc.wideRow("Change name", () -> askProfileName("Profile name", Prefs.userName(cc), n -> {
            Prefs.setUserName(cc, n);
            cc.pill.refreshAvatar();
            showEditProfile();
        }));
        if (Profiles.all(cc).size() > 1) cc.wideRow("Remove profile", this::confirmRemoveProfile);
        if (focus != null) focus.requestFocus();
    }

    private LinearLayout avatarRow() {
        LinearLayout row = new LinearLayout(cc);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.bottomMargin = Ui.dp(cc, 10);
        cc.body.addView(row, rlp);
        return row;
    }

    private TextView avatarCell(LinearLayout row, boolean gap) {
        TextView a = new TextView(cc);
        a.setGravity(Gravity.CENTER);
        a.setIncludeFontPadding(false);
        a.setFocusable(true);
        a.setClickable(true);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(Ui.dp(cc, 40), Ui.dp(cc, 40));
        if (gap) alp.leftMargin = Ui.dp(cc, 12);
        row.addView(a, alp);
        return a;
    }

    /** Nach dem Wählen einer Farbe den Fokus auf derselben Farbe lassen. */
    private void focusColor(int color) {
        int seen = 0;
        for (int i = 0; i < cc.body.getChildCount(); i++) {
            View child = cc.body.getChildAt(i);
            if (child instanceof TextView && "Background".contentEquals(((TextView) child).getText())) {
                seen = 1;
                continue;
            }
            if (seen > 0 && child instanceof LinearLayout && ((LinearLayout) child).getChildCount() > 0
                    && ((LinearLayout) child).getChildAt(0) instanceof TextView) {
                LinearLayout row = (LinearLayout) child;
                if (color < row.getChildCount()) {
                    row.getChildAt(color).requestFocus();
                    return;
                }
                color -= row.getChildCount();
            }
        }
    }

    /** Overlay mit allen Emojis; die Leiste bleibt offen, solange es offen ist. */
    private void showAllEmojis(String current) {
        cc.handler.removeCallbacks(cc.idleClose);
        String id = Profiles.currentId(cc);
        android.app.Dialog d = Avatars.chooser(cc, current, emoji -> {
            Profiles.setPicture(cc, id, emoji, null);
            cc.pill.refreshAvatar();
            showEditProfile();
        });
        d.setOnDismissListener(x -> cc.poke());
        d.show();
    }

    private void confirmRemoveProfile() {
        cc.body.removeAllViews();
        cc.body.setTag("profile_remove");
        TextView q = Ui.text(cc, "Remove " + Prefs.userName(cc) + "?", 15, Ui.TEXT, true);
        q.setPadding(Ui.dp(cc, 4), 0, 0, Ui.dp(cc, 4));
        cc.body.addView(q);
        TextView info = Ui.dimText(cc, "Its tiles and folders will be deleted.");
        info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        info.setPadding(Ui.dp(cc, 4), 0, 0, Ui.dp(cc, 6));
        cc.body.addView(info);
        View keep = cc.wideRow("Keep", this::showEditProfile);
        cc.wideRow("Remove", () -> {
            String id = Profiles.currentId(cc);
            Profiles.remove(cc, id);
            switchProfile(Profiles.currentId(cc));
        });
        keep.requestFocus();
    }

    private interface NameCallback {
        void done(String name);
    }

    private void askProfileName(String title, String preset, NameCallback callback) {
        cc.handler.removeCallbacks(cc.idleClose);
        Dialogs.Panel p = Dialogs.askText(cc, title, preset, n -> {
            if (!n.isEmpty()) callback.done(n);
        });
        p.dialog.setOnDismissListener(d -> cc.poke());
    }
}
