## 🚀 Milestone: (Global / Saga) Transactional support for entities
 🌌 Post 1.0 Era

### 📝 Milestone Description

So far NeonBees write support is limited to DataVerticles implementing the `DataSink` interface and to the general capabilities of e.g. the OData endpoint to comply to the OASIS OData V4 standard providing write support for single entities. Often when performing write operations, orchestration of some sort is required. Either by having consolidated operations performed on multiple cluster nodes, or even spanning multiple different write operations into one atomic transaction. This requires concepts like rollbacks and sagas to be supported in future. Also deterministic transactions, which can be retried in case of a failed delivery are required to perform transactions reliably.

A common use case of NeonBee is to process data from several remote sources via DataVerticles in an asynchronous way. This asynchronous (and parallel) processing is important to ensure good performance. NeonBee acts only as a data processing layer for tasks like aggregation, mapping and so on, but the NeonBee cluster does not own the data, because the data is owned and stored by the remote sources. Due to the fact that NeonBee does not own and store the data in a close coupled way (e.g. JDBC database), there is no option for local transactions to ensure data consistency. If some local transaction fails, the whole global transaction should fail and a rollback of all local transactions which where involved (and prepared). A saga is a sequence of transactions that is executed. Furthermore a saga defines a compensation transaction for every local transaction.

## Tasks / Features

- Add a local / global / saga transaction support into DataVerticles
- Add possibilities to prepare and post process transactions and DataVerticles to rollback made changes, while keeping the asynchronous nature of NeonBee