package com.example.taketoday.venue;

/** Районы Бишкека. */
public enum District {
    PERVOMAISKY("Первомайский район"),
    OKTYABRSKY("Октябрьский район"),
    LENINSKY("Ленинский район"),
    SVERDLOVSKY("Свердловский район");

    private final String title;

    District(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
