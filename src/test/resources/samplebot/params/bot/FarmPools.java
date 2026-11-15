package samplebot.params.bot;

import com.botmaker.plugin.api.params.Pool;
import com.botmaker.plugin.api.params.Pools;

/** What several runs at once are dealt from: each run its own account. */
@Pools
public class FarmPools {

    public Pool<String> accounts() {
        return Pool.of("account-1@example.com", "account-2@example.com").fills(Farm::account);
    }
}
