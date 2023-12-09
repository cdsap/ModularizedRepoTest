## Example Talaiot Publisher using BigQuery
This repository how to create a custom publisher using BigQuery.

```
talaiot {
    publishers {
        jsonPublisher = true
        customPublishers(BiqQueryPublisher())
    }
}
```

The custom publisher is compatible with configuration cache.
It requires the Credential json file.
