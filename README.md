# ph-ee-dpg-core
# dpga
Digital Public Goods Alliance middleware for integration to real-time payment systems.

Netflix Conductor is used as the orchestration tool.

# Modules

There are 4 modules currently
- channel Connector - to initiate transfer request
- exporter - Export data from Conductor to Kafka
- importer - Import, parse and save data to mysql database
- ams-mifos - has workers that consume Fineract(ams) api