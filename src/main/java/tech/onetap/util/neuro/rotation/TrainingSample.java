package tech.onetap.util.neuro.rotation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrainingSample {
    private float[] input;
    private float[] output;
    private int age;

    public TrainingSample(float[] input, float[] output) {
        this.input = input;
        this.output = output;
        this.age = 0;
    }
}
