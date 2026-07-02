package tech.onetap.util.neuro.rotation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrainingSample {
    private float[] input;  // [prevDeltaYaw, prevDeltaPitch, targetDeltaYaw, targetDeltaPitch]
    private float[] output; // [actualDeltaYaw, actualDeltaPitch]
}
