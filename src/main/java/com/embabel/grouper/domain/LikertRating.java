package com.embabel.grouper.domain;

/**
 * Likert rating that can be converted to a normalized score
 */
public record LikertRating(Scale scale) {

    /**
     * Likert scale rating
     */
    enum Scale {
        STRONGLY_DISAGREE(0.0),
        DISAGREE(0.25),
        NEUTRAL(0.5),
        AGREE(0.75),
        STRONGLY_AGREE(1.0);

        private final double value;

        Scale(double value) {
            this.value = value;
        }

        public double getValue() {
            return value;
        }
    }

    /**
     * Return a score from 0-1 where 0 is strongly disagree
     */
    public double score() {
        return scale.getValue();
    }
}
