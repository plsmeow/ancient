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
    private SampleQuality quality;
    private SampleSource source;
    private int tick;

    public TrainingSample(float[] input, float[] output) {
        this(input, output, SampleQuality.CLEAN, SampleSource.HUMAN, 0);
    }

    public TrainingSample(float[] input, float[] output, SampleQuality quality, SampleSource source, int tick) {
        this.input = input;
        this.output = output;
        this.age = 0;
        this.quality = quality;
        this.source = source;
        this.tick = tick;
    }
}
