package com.qianniu.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SceneHeading {
    /** INT / EXT / INT/EXT */
    private String locationType;
    private String place;
    /** DAY / NIGHT / DUSK / DAWN / CONTINUOUS / LATER / MOMENTS LATER */
    private String time;
}
