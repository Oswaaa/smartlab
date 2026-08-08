package com.smartlab.engine.observation;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Read-only observation boundary exposed to logical consumers such as constraints. */
public interface ObservableSnapshotReader {
    ObservationSnapshot read(ObservableKey key);

    Map<ObservableKey, ObservationSnapshot> readBatch(Collection<ObservableKey> keys);

    List<ObservationSample> readHistory(ObservableKey key, Instant since);

    Map<ObservableKey, List<ObservationSample>> readHistoryBatch(Collection<ObservableKey> keys, Instant since);
}
