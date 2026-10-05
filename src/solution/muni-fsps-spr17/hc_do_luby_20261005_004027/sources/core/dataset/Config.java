package dataset;

/**
 * This class represents Configurations as defined in the ITC 2019. Each configuration consists of one or more
 * subparts with their unique ids
 */
public record Config(int id, Subpart[] subparts) {
    /**
     * Constructs a Config object.
     *
     * @param id       The id of the config.
     * @param subparts The list of subparts of this config.
     * @throws IllegalArgumentException If the config id is invalid.
     */
    public Config {
        if (id < 1) throw new IllegalArgumentException("Config id cannot be less than 1!");
    }
}