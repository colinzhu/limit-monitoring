# Requirements Document

## Introduction

The Payment Limit Monitoring System is a financial risk management application that tracks settlement flows from trading systems, calculates aggregated exposure by counterparty and value date, and flags transactions exceeding predefined limits for operational review. The system ensures compliance with risk management policies by providing real-time monitoring and manual approval workflows for high-value settlement groups.

## Glossary

- **Payment_Limit_Monitoring_System**: The software system that monitors settlement flows and enforces exposure limits
- **Settlement**: A financial transaction record containing payment information between entities
- **PTS**: Primary Trading System - the source system generating settlement data
- **Processing_Entity**: A business unit within a trading system that processes settlements
- **Counterparty**: The external party involved in a settlement transaction
- **Value_Date**: The date when a settlement is scheduled to be processed
- **Settlement_ID**: Unique identifier for a settlement transaction
- **Settlement_Version**: Version number for a settlement, as settlements can be modified over time
- **Subtotal**: Aggregated USD equivalent amount for settlements grouped by PTS, Processing Entity, Counterparty, and Value Date
- **Exposure_Limit**: Maximum allowed USD amount for a settlement group, either fixed at 500 million USD (MVP) or counterparty-specific limits fetched from external systems
- **Operation_Team**: Users responsible for reviewing and approving settlements that exceed limits
- **CREATED**: Settlement status when the group subtotal is within the exposure limit
- **BLOCKED**: Settlement status when the group subtotal exceeds the exposure limit
- **PENDING_AUTHORISE**: Settlement status after an operation team member requests release but before authorization
- **AUTHORISED**: Settlement status after a second operation team member authorizes the release
- **Exchange_Rate**: Currency conversion rate automatically fetched from external systems and used for USD equivalent calculations
- **Filtering_Rules**: Configurable criteria stored in an external rule system that determine which settlements should be included in subtotal calculations
- **Rule_System**: External system that manages and provides filtering rules for settlement inclusion criteria

## Requirements

### Requirement 1

**User Story:** As a risk manager, I want to receive and process settlement flows from trading systems, so that I can monitor payment exposures in real-time.

#### Acceptance Criteria

1. WHEN a settlement flow is received from an endpoint, THE Payment_Limit_Monitoring_System SHALL validate and store the settlement data including PTS, Processing_Entity, Counterparty_ID, Value_Date, Currency, Amount, Settlement_ID, and Settlement_Version
2. WHEN a settlement is received, THE Payment_Limit_Monitoring_System SHALL evaluate the settlement against current filtering rules to determine if it should be included in subtotal calculations
3. WHEN a settlement matches the filtering criteria, THE Payment_Limit_Monitoring_System SHALL include it in group subtotal calculations and limit monitoring
4. WHEN a settlement does not match the filtering criteria, THE Payment_Limit_Monitoring_System SHALL store the settlement but exclude it from subtotal calculations and limit monitoring
5. WHEN multiple versions of the same Settlement_ID are received, THE Payment_Limit_Monitoring_System SHALL maintain the latest version and preserve historical versions for audit purposes
6. WHEN settlement data is stored, THE Payment_Limit_Monitoring_System SHALL preserve the original currency and amount while enabling USD equivalent calculation for subtotal aggregation
7. WHEN a settlement flow contains invalid or incomplete data, THE Payment_Limit_Monitoring_System SHALL reject the settlement and log the error for investigation
8. THE Payment_Limit_Monitoring_System SHALL fetch the latest filtering rules from the external rule system every 5 minutes to ensure current criteria are applied
9. THE Payment_Limit_Monitoring_System SHALL process settlement flows continuously without interruption

### Requirement 2

**User Story:** As a risk manager, I want settlements to be grouped and aggregated by key dimensions, so that I can calculate total exposure per counterparty and value date.

#### Acceptance Criteria

1. WHEN settlements are processed, THE Payment_Limit_Monitoring_System SHALL group settlements by PTS, Processing_Entity, Counterparty_ID, and Value_Date
2. WHEN calculating subtotals, THE Payment_Limit_Monitoring_System SHALL convert individual settlement amounts to USD equivalent and sum them within each group
3. WHEN a new settlement is received for an existing group, THE Payment_Limit_Monitoring_System SHALL recalculate the subtotal immediately
4. WHEN a settlement version is updated, THE Payment_Limit_Monitoring_System SHALL adjust the subtotal to reflect the new amount and remove the previous version's contribution
5. WHEN a settlement version updates the Counterparty_ID, PTS, Processing_Entity, or Value_Date, THE Payment_Limit_Monitoring_System SHALL move the settlement to the appropriate new group, remove it from the old group, and recalculate subtotals for both affected groups
6. WHEN a settlement receives a new version, THE Payment_Limit_Monitoring_System SHALL reset any existing approval status and re-evaluate the settlement status based on the updated information and current group subtotal
7. WHEN filtering rules are updated from the rule system, THE Payment_Limit_Monitoring_System SHALL re-evaluate existing settlements against the new criteria and adjust subtotal calculations accordingly
8. THE Payment_Limit_Monitoring_System SHALL maintain accurate subtotals across all active settlement groups

### Requirement 3

**User Story:** As a risk manager, I want to track settlement status based on group exposure limits, so that I can ensure proper oversight of high-value transactions.

#### Acceptance Criteria

1. WHEN a settlement is processed and its group subtotal is within the Exposure_Limit, THE Payment_Limit_Monitoring_System SHALL set the settlement status to CREATED
2. WHEN a settlement is processed and its group subtotal exceeds the Exposure_Limit, THE Payment_Limit_Monitoring_System SHALL set the settlement status to BLOCKED
3. WHEN the Exposure_Limit is updated, THE Payment_Limit_Monitoring_System SHALL re-evaluate all settlement statuses against the new limit
4. WHEN a settlement moves between groups due to version updates, THE Payment_Limit_Monitoring_System SHALL update the settlement status based on the new group's subtotal
5. THE Payment_Limit_Monitoring_System SHALL display settlement records with their current status in the user interface

### Requirement 4

**User Story:** As an operations team member, I want to review and approve individual settlements that are blocked due to limit exceedance, so that I can ensure compliance with risk management policies through a two-step approval process.

#### Acceptance Criteria

1. WHEN viewing BLOCKED settlements, THE Payment_Limit_Monitoring_System SHALL display the settlement details and group information including PTS, Processing_Entity, Counterparty_ID, Value_Date, and current group subtotal
2. WHEN an operation team member clicks REQUEST RELEASE for a BLOCKED settlement, THE Payment_Limit_Monitoring_System SHALL change the settlement status to PENDING_AUTHORISE and record the action with user identity and timestamp
3. WHEN a different operation team member clicks AUTHORISE for a PENDING_AUTHORISE settlement, THE Payment_Limit_Monitoring_System SHALL change the settlement status to AUTHORISED and record the action with user identity and timestamp
4. WHEN the same operation team member attempts to perform both REQUEST RELEASE and AUTHORISE actions on the same settlement, THE Payment_Limit_Monitoring_System SHALL prevent the action and display an error message
5. WHEN a settlement receives a new version after user actions have been taken, THE Payment_Limit_Monitoring_System SHALL reset the settlement status to CREATED or BLOCKED based on the new group subtotal and invalidate all previous approval actions
6. WHEN selecting multiple settlements for bulk actions, THE Payment_Limit_Monitoring_System SHALL only allow selection of settlements that belong to the same group (same PTS, Processing_Entity, Counterparty_ID, and Value_Date)
7. WHEN performing bulk REQUEST RELEASE or AUTHORISE actions, THE Payment_Limit_Monitoring_System SHALL apply the action to all selected settlements and record individual audit entries for each settlement
8. THE Payment_Limit_Monitoring_System SHALL maintain a complete audit trail of all user actions including REQUEST RELEASE and AUTHORISE operations with timestamps, user identities, settlement details, and version information

### Requirement 5

**User Story:** As a system user, I want the system to handle high-volume settlement processing with acceptable performance, so that I can access current settlement status without excessive delays during peak trading periods.

#### Acceptance Criteria

1. WHEN processing settlement flows during peak periods, THE Payment_Limit_Monitoring_System SHALL handle up to 200,000 settlements within 30 minutes without system failure
2. WHEN a settlement is received and processed, THE Payment_Limit_Monitoring_System SHALL make the updated status available in the user interface within 30 seconds of receipt
3. WHEN calculating subtotals for settlement groups, THE Payment_Limit_Monitoring_System SHALL complete the calculation and status update within 10 seconds of receiving a new settlement
4. WHEN users query settlement status via API, THE Payment_Limit_Monitoring_System SHALL respond within 3 seconds under normal load conditions
5. THE Payment_Limit_Monitoring_System SHALL maintain acceptable response times for user interface operations even during peak settlement processing periods

### Requirement 6

**User Story:** As an operations team member, I want to search and filter settlements using multiple criteria, so that I can efficiently locate specific settlements for review and management.

#### Acceptance Criteria

1. WHEN using the search interface, THE Payment_Limit_Monitoring_System SHALL allow filtering by PTS, Processing_Entity, Value_Date, and Counterparty_ID
2. WHEN searching settlements, THE Payment_Limit_Monitoring_System SHALL provide a filter option to show only settlements in groups that exceed the limit or only those that do not exceed the limit
3. WHEN multiple search criteria are applied, THE Payment_Limit_Monitoring_System SHALL return settlements that match all specified criteria
4. WHEN displaying search results, THE Payment_Limit_Monitoring_System SHALL show settlement details, current status, and group subtotal information
5. THE Payment_Limit_Monitoring_System SHALL provide search results in a paginated format for efficient browsing of large result sets
6. WHEN users want to export search results, THE Payment_Limit_Monitoring_System SHALL allow downloading the filtered settlements as an Excel file containing all relevant settlement details and status information
7. WHEN displaying the user interface, THE Payment_Limit_Monitoring_System SHALL show settlement groups in the upper section and individual settlements in the lower section
8. WHEN a user clicks on a settlement group in the upper section, THE Payment_Limit_Monitoring_System SHALL display all settlements belonging to that group in the lower section with their individual details and statuses
9. WHEN displaying settlement information, THE Payment_Limit_Monitoring_System SHALL show sufficient context including group subtotal in USD, exposure limit, current exchange rates as reference for currency conversion, and filtering rule application status to explain why settlements are BLOCKED or not BLOCKED

### Requirement 7

**User Story:** As an external system, I want to query settlement status via API, so that I can make informed processing decisions based on current settlement approval status.

#### Acceptance Criteria

1. WHEN an external system queries by Settlement_ID, THE Payment_Limit_Monitoring_System SHALL return the current settlement status (CREATED, BLOCKED, PENDING_AUTHORISE, or AUTHORISED)
2. WHEN a settlement status is BLOCKED, THE Payment_Limit_Monitoring_System SHALL include detailed information explaining why the settlement is blocked, including group subtotal, exposure limit, and affected counterparty details
3. WHEN a settlement status is PENDING_AUTHORISE or AUTHORISED, THE Payment_Limit_Monitoring_System SHALL include approval workflow information including timestamps and user actions taken
4. WHEN a Settlement_ID is not found, THE Payment_Limit_Monitoring_System SHALL return an appropriate error response with clear messaging
5. THE Payment_Limit_Monitoring_System SHALL provide API responses in a structured format with sufficient detail to prevent follow-up queries for clarification
6. WHEN a settlement status changes to AUTHORISED, THE Payment_Limit_Monitoring_System SHALL send a notification to external systems containing the Settlement_ID and authorization details to trigger downstream processing
7. WHEN a manual recalculation is requested via API endpoint with scope criteria (PTS, Processing_Entity, from_Value_Date), THE Payment_Limit_Monitoring_System SHALL recalculate subtotals and update settlement statuses for all settlements matching the specified criteria
8. WHEN performing manual recalculation, THE Payment_Limit_Monitoring_System SHALL apply current filtering rules and exposure limits to determine updated settlement statuses within the specified scope

### Requirement 8

**User Story:** As a system administrator, I want to configure exposure limits and currency conversion rates, so that the system can adapt to changing business requirements and market conditions.

#### Acceptance Criteria

1. WHEN operating in MVP mode, THE Payment_Limit_Monitoring_System SHALL use a fixed Exposure_Limit of 500 million USD for all counterparties
2. WHEN configured for advanced mode, THE Payment_Limit_Monitoring_System SHALL fetch counterparty-specific exposure limits from an external system daily and apply the appropriate limit based on the settlement's Counterparty_ID
3. THE Payment_Limit_Monitoring_System SHALL automatically fetch and store exchange rates from external systems daily for currency conversion
4. WHEN currency conversion is required, THE Payment_Limit_Monitoring_System SHALL use the latest available exchange rate at the time of settlement processing to convert amounts to USD equivalent
5. WHEN new exchange rates are fetched and stored, THE Payment_Limit_Monitoring_System SHALL make them available for future settlement processing without recalculating existing subtotals
6. WHEN counterparty-specific limits are updated, THE Payment_Limit_Monitoring_System SHALL re-evaluate all affected settlement groups against their respective new limits
7. THE Payment_Limit_Monitoring_System SHALL log all configuration changes and limit updates with timestamps and system identity

### Requirement 9

**User Story:** As a compliance officer, I want to access historical settlement data and review decisions, so that I can perform audits and ensure regulatory compliance.

#### Acceptance Criteria

1. WHEN querying historical data, THE Payment_Limit_Monitoring_System SHALL provide access to all settlement versions and their timestamps
2. WHEN reviewing audit trails, THE Payment_Limit_Monitoring_System SHALL display all review actions, approvals, and system calculations with full traceability
3. WHEN generating reports, THE Payment_Limit_Monitoring_System SHALL export settlement data, subtotals, and review status in standard formats
4. WHEN data retention policies apply, THE Payment_Limit_Monitoring_System SHALL archive historical data while maintaining accessibility for compliance periods
5. THE Payment_Limit_Monitoring_System SHALL ensure data integrity and prevent unauthorized modifications to historical records