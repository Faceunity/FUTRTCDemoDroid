package com.faceunity.nama.ui.enums;

import com.faceunity.nama.R;
import com.faceunity.nama.entity.Sticker;

import java.util.ArrayList;
import java.util.List;

/**
 * 道具贴纸列表
 *
 * @author Richie on 2019.12.20
 */
public enum StickerEnum {
    /**
     * 道具贴纸
     */
    STICKER_none(R.drawable.ic_delete_all, "", "none"),
    STICKER_sdlu(R.drawable.sdlu, "sticker/sdlu.bundle", "sdlu"),
    STICKER_daisypig(R.drawable.daisypig, "sticker/daisypig.bundle", "daisypig"),
    STICKER_fashi(R.drawable.fashi, "sticker/fashi.bundle", "fashi"),
    STICKER_xueqiu_lm_fu(R.drawable.xueqiu_lm_fu, "sticker/xueqiu_lm_fu.bundle", "xueqiu_lm_fu"),
    STICKER_wobushi(R.drawable.wobushi, "sticker/wobushi.bundle", "wobushi"),
    STICKER_gaoshiqing(R.drawable.gaoshiqing, "sticker/gaoshiqing.bundle", "gaoshiqing");

    private int iconId;
    private String filePath;
    private String description;

    StickerEnum(int iconId, String filePath, String description) {
        this.iconId = iconId;
        this.filePath = filePath;
        this.description = description;
    }

    public Sticker create() {
        return new Sticker(iconId, filePath, description);
    }

    public static List<Sticker> getStickers() {
        StickerEnum[] stickerEnums = StickerEnum.values();
        List<Sticker> stickers = new ArrayList<>(stickerEnums.length);
        for (StickerEnum e : stickerEnums) {
            stickers.add(e.create());
        }
        return stickers;
    }

}
