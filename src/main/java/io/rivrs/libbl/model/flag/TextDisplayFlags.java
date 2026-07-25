package io.rivrs.libbl.model.flag;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum TextDisplayFlags implements Flag {
    HAS_SHADOW(0x01),
    IS_SEE_THROUGH(0x02),
    USE_DEFAULT_BACKGROUND_COLOR(0x04),
    // Alignement (bits 3-4) : valeur 0 = centre, 1 = gauche, 2 = droite. Poser au plus un des deux.
    ALIGN_LEFT(0x08),
    ALIGN_RIGHT(0x10),
    ;

    private final int value;
}
