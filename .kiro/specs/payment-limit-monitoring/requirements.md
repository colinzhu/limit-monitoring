# Requirements Document

## Introduction

The Payment Limit Monitoring System is a financial risk management application that tracks settlement flows from trading systems, calculates aggregated exposure by counterparty and value date, and flags transactions exceeding predefined limits for operational review. The system ensures compliance with risk management policies by providing real-time monitoring and manual approval workflows for high-value settlement groups.

## Glossary

- **Payment_Limit_Monitoring_System**: The software system that monitors settlement flows and enforces exposure limits
- **Settlement**: A financial transaction record containing payment information between entities
- **Settlement_Direction**: The direction of a settlement transaction, either PAY (outgoing payment) or RECEIVE (incoming payment)
- **Settlement_Type**: The settlement processing type, either NET (netted from multiple settlements) or GROSS (individual settlement)
- **Business_Status**: The settlement status from the PTS system, which can be PENDING, INVALID, VERIFIED, or CANCELLED
- **PAY_Settlement**: A settlement with direction PAY that represents an outgoing payment and contributes to risk exposure
- **RECEIVE_Settlement**: A settlement with direction RECEIVE that represents an incoming payment and does not contribute to risk exposure
- **NET_Settlement**: A netted settlement that can frequently change between PAY and RECEIVE directions based on the net result of multiple underlying settlements
- **GROSS_Settlement**: An individual settlement that maintains a consistent direction throughout its lifecycle
- **VERIFIED_Settlement**: A settlement with business status VERIFIED that is confirmed in PTS but can still be cancelled, and is eligible for manual approval workflows when blocked
- **CANCELLED_Settlement**: A settlement with business status CANCELLED that should be excluded from subtotal calculations
- **PENDING_Settlement**: A settlement with business status PENDING that is incomplete in PTS but should be included in subtotal calculations, however not eligible for manual approval workflows until verified
- **INVALID_Settlement**: A settlement with business status INVALID that is incomplete in PTS but should be included in subtotal calculations, however not eligible for manual approval workflows until verified
- **PTS**: Primary Trading System - the source system generating settlement data
- **Processing_Entity**: A business unit within a trading system that processes settlements
- **Counterparty**: The external party involved in a settlement transaction
- **Value_Date**: The date when a settlement is scheduled to be processed
- **Settlement_ID**: Unique identifier for a settlement transaction
- **Settlement_Version**: Version number for a settlement, as settlements can be modified over time
- **Subtotal**: Aggregated USD equivalent amount for settlements grouped by PTS, Processing Entity, Counterparty, and Value Date
- **Exposure_Limit**: Maximum allowed USD amount for a settlement group, which is configurable and can be updated frequently. In MVP mode, a fixed limit of 500 million USD applies to all counterparties. In advanced mode, counterparty-specific limits are fetched from external systems. When limits are updated, the system must immediately reflect the new limits without requiring mass database updates or lengthy recalculation processes.
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

1. WHEN a settlement flow is received from an endpoint, THE Payment_Limit_Monitoring_System SHALL validate and store the settlement data including PTS, Processing_Entity, Counterparty_ID, Value_Date, Currency, Amount, Settlement_ID, Settlement_Version, Settlement_Direction (PAY or RECEIVE), Settlement_Type (NET or GROSS), and Business_Status (PENDING, INVALID, VERIFIED, or CANCELLED)
2. WHEN a settlement is received, THE Payment_Limit_Monitoring_System SHALL evaluate the settlement against current filtering rules to determine if it should be included in subtotal calculations
3. WHEN a settlement has direction PAY and business status is PENDING, INVALID, or VERIFIED, THE Payment_Limit_Monitoring_System SHALL include it in group subtotal calculations for risk exposure monitoring
4. WHEN a settlement has direction RECEIVE or business status is CANCELLED, THE Payment_Limit_Monitoring_System SHALL store the settlement but exclude it from subtotal calculations
5. WHEN a NET settlement is received, THE Payment_Limit_Monitoring_System SHALL handle potential direction changes between PAY and RECEIVE as the netted result may fluctuate based on underlying settlement updates
6. WHEN a settlement matches the filtering criteria, has direction PAY, and business status is not CANCELLED, THE Payment_Limit_Monitoring_System SHALL include it in group subtotal calculations and limit monitoring
7. WHEN a settlement does not match the filtering criteria, has direction RECEIVE, or has business status CANCELLED, THE Payment_Limit_Monitoring_System SHALL store the settlement but exclude it from subtotal calculations and limit monitoring
8. WHEN multiple versions of the same Settlement_ID are received, THE Payment_Limit_Monitoring_System SHALL maintain the latest version and preserve historical versions for audit purposes
9. WHEN settlement versions are received out of chronological order, THE Payment_Limit_Monitoring_System SHALL determine the correct latest version based on Settlement_Version number and apply only the most recent version to subtotal calculations
10. WHEN an older version of a settlement arrives after a newer version has already been processed, THE Payment_Limit_Monitoring_System SHALL store the historical version but SHALL NOT recalculate subtotals or update settlement statuses
11. WHEN multiple settlement updates for the same group are processed concurrently across different system instances, THE Payment_Limit_Monitoring_System SHALL ensure subtotal calculation consistency through appropriate concurrency control mechanisms
12. WHEN concurrent settlement processing occurs, THE Payment_Limit_Monitoring_System SHALL prevent subtotal overwrites and ensure that the final subtotal reflects all valid settlement updates within the group
9. WHEN a settlement version updates the business status from VERIFIED to CANCELLED, THE Payment_Limit_Monitoring_System SHALL exclude the settlement from subtotal calculations and recalculate the group total using appropriate concurrency control to prevent race conditions
10. WHEN a settlement version updates the business status from CANCELLED to PENDING, INVALID, or VERIFIED, THE Payment_Limit_Monitoring_System SHALL include the settlement in subtotal calculations if it has direction PAY and recalculate the group total using appropriate concurrency control to prevent race conditions
11. WHEN settlement data is stored, THE Payment_Limit_Monitoring_System SHALL preserve the original currency and amount while enabling USD equivalent calculation for subtotal aggregation
12. WHEN a settlement flow contains invalid or incomplete data, THE Payment_Limit_Monitoring_System SHALL reject the settlement and log the error for investigation
13. THE Payment_Limit_Monitoring_System SHALL fetch the latest filtering rules from the external rule system every 5 minutes to ensure current criteria are applied
14. THE Payment_Limit_Monitoring_System SHALL process settlement flows continuously without interruption

### Requirement 2

**User Story:** As a risk manager, I want settlements to be grouped and aggregated by key dimensions, so that I can calculate total exposure per counterparty and value date.

#### Acceptance Criteria

1. WHEN settlements are processed, THE Payment_Limit_Monitoring_System SHALL group PAY settlements with business status PENDING, INVALID, or VERIFIED by PTS, Processing_Entity, Counterparty_ID, and Value_Date
2. WHEN calculating subtotals, THE Payment_Limit_Monitoring_System SHALL recalculate the complete group subtotal by summing all currently included PAY settlements with business status not CANCELLED, rather than incrementally adding or subtracting individual settlement changes
3. WHEN a new PAY settlement with business status PENDING, INVALID, or VERIFIED is received for an existing group, THE Payment_Limit_Monitoring_System SHALL recalculate the complete group subtotal immediately
4. WHEN a settlement version is updated with a new amount, direction change, or business status change, THE Payment_Limit_Monitoring_System SHALL recalculate the complete group subtotal using all current settlement versions rather than applying incremental changes
5. WHEN a settlement version changes its inclusion criteria (direction, business status, or filtering rule match), THE Payment_Limit_Monitoring_System SHALL recalculate the complete group subtotal to reflect the current set of included settlements
6. WHEN a NET settlement direction changes between PAY and RECEIVE due to underlying settlement updates, THE Payment_Limit_Monitoring_System SHALL recalculate the complete group subtotal to reflect the current settlement state
7. WHEN a settlement version updates the Counterparty_ID, PTS, Processing_Entity, or Value_Date, THE Payment_Limit_Monitoring_System SHALL move the settlement to the appropriate new group and recalculate complete subtotals for both affected groups using all current settlement versions in each group
8. WHEN a settlement receives a new version, THE Payment_Limit_Monitoring_System SHALL reset any existing approval status and re-evaluate the settlement status based on the updated information and recalculated group subtotal
9. WHEN filtering rules are updated from the rule system, THE Payment_Limit_Monitoring_System SHALL re-evaluate existing settlements against the new criteria and recalculate complete subtotals for all affected groups
10. THE Payment_Limit_Monitoring_System SHALL maintain accurate subtotals across all active settlement groups by performing complete recalculation rather than incremental updates to ensure data consistency

### Requirement 3

**User Story:** As a risk manager, I want to track settlement status based on group exposure limits, so that I can ensure proper oversight of high-value transactions.

#### Acceptance Criteria

1. WHEN a PAY settlement with business status PENDING, INVALID, or VERIFIED is processed and its group subtotal is within the Exposure_Limit, THE Payment_Limit_Monitoring_System SHALL set the settlement status to CREATED
2. WHEN a PAY settlement with business status PENDING, INVALID, or VERIFIED is processed and its group subtotal exceeds the Exposure_Limit, THE Payment_Limit_Monitoring_System SHALL set the settlement status to BLOCKED
3. WHEN a RECEIVE settlement or a settlement with business status CANCELLED is processed, THE Payment_Limit_Monitoring_System SHALL set the settlement status to CREATED as these settlements do not contribute to risk exposure
4. WHEN a NET settlement changes direction from RECEIVE to PAY with business status not CANCELLED and causes the group subtotal to exceed the Exposure_Limit, THE Payment_Limit_Monitoring_System SHALL update the settlement status to BLOCKED
5. WHEN a NET settlement changes direction from PAY to RECEIVE or business status changes to CANCELLED, THE Payment_Limit_Monitoring_System SHALL update the settlement status to CREATED and recalculate the complete group subtotal
6. WHEN the Exposure_Limit is updated, THE Payment_Limit_Monitoring_System SHALL re-evaluate all settlement statuses against the new limit
4. WHEN a settlement moves between groups due to version updates, THE Payment_Limit_Monitoring_System SHALL update the settlement status based on the new group's subtotal
5. THE Payment_Limit_Monitoring_System SHALL display settlement records with their current status in the user interface

### Requirement 4

**User Story:** As an operations team member, I want to review and approve individual settlements that are blocked due to limit exceedance, so that I can ensure compliance with risk management policies through a two-step approval process.

#### Acceptance Criteria

1. WHEN viewing BLOCKED settlements, THE Payment_Limit_Monitoring_System SHALL display only PAY settlements with business status VERIFIED that are blocked due to limit exceedance, along with settlement details and group information including PTS, Processing_Entity, Counterparty_ID, Value_Date, Settlement_Direction, Settlement_Type, Business_Status, and current group subtotal
2. WHEN an operation team member clicks REQUEST RELEASE for a BLOCKED PAY settlement with business status VERIFIED, THE Payment_Limit_Monitoring_System SHALL change the settlement status to PENDING_AUTHORISE and record the action with user identity and timestamp
3. WHEN a different operation team member clicks AUTHORISE for a PENDING_AUTHORISE settlement with business status VERIFIED, THE Payment_Limit_Monitoring_System SHALL change the settlement status to AUTHORISED and record the action with user identity and timestamp
4. WHEN the same operation team member attempts to perform both REQUEST RELEASE and AUTHORISE actions on the same settlement, THE Payment_Limit_Monitoring_System SHALL prevent the action and display an error message
5. WHEN a settlement receives a new version that changes its business status from VERIFIED to PENDING, INVALID, or CANCELLED, THE Payment_Limit_Monitoring_System SHALL reset the settlement status based on the new business status and group subtotal, and invalidate all previous approval actions
6. WHEN a settlement with business status PENDING or INVALID is blocked due to limit exceedance, THE Payment_Limit_Monitoring_System SHALL display the settlement as BLOCKED but SHALL NOT provide REQUEST RELEASE functionality until the business status becomes VERIFIED
6. WHEN selecting multiple settlements for bulk actions, THE Payment_Limit_Monitoring_System SHALL only allow selection of VERIFIED settlements that belong to the same group (same PTS, Processing_Entity, Counterparty_ID, and Value_Date)
7. WHEN performing bulk REQUEST RELEASE or AUTHORISE actions, THE Payment_Limit_Monitoring_System SHALL apply the action to all selected VERIFIED settlements and record individual audit entries for each settlement
8. THE Payment_Limit_Monitoring_System SHALL maintain a complete audit trail of all user actions including REQUEST RELEASE and AUTHORISE operations with timestamps, user identities, settlement details, and version information for VERIFIED settlements only

### Requirement 5

**User Story:** As a system user, I want the system to handle high-volume settlement processing with acceptable performance, so that I can access current settlement status without excessive delays during peak trading periods.

#### Acceptance Criteria

1. WHEN processing settlement flows during peak periods, THE Payment_Limit_Monitoring_System SHALL handle up to 200,000 settlements within 30 minutes without system failure while maintaining data consistency across multiple system instances
2. WHEN a settlement is received and processed, THE Payment_Limit_Monitoring_System SHALL make the updated status available in the user interface within 30 seconds of receipt, ensuring consistency across all system instances
2. WHEN calculating subtotals for PAY settlement groups, THE Payment_Limit_Monitoring_System SHALL complete the recalculation within 10 seconds of receiving a new settlement, accounting for potential direction changes in NET settlements and ensuring complete group recalculation rather than incremental updates, while preventing concurrent calculation conflicts
4. WHEN users query settlement status via API, THE Payment_Limit_Monitoring_System SHALL respond within 3 seconds under normal load conditions
5. THE Payment_Limit_Monitoring_System SHALL maintain acceptable response times for user interface operations even during peak settlement processing periods

### Requirement 6

**User Story:** As an operations team member, I want to search and filter settlements using multiple criteria, so that I can efficiently locate specific settlements for review and management.

#### Acceptance Criteria

1. WHEN using the search interface, THE Payment_Limit_Monitoring_System SHALL allow filtering by PTS, Processing_Entity, Value_Date, Counterparty_ID, Settlement_Direction (PAY or RECEIVE), Settlement_Type (NET or GROSS), and Business_Status (PENDING, INVALID, VERIFIED, or CANCELLED)
2. WHEN searching settlements, THE Payment_Limit_Monitoring_System SHALL provide a filter option to show only PAY settlements with business status not CANCELLED in groups that exceed the limit, only PAY settlements that do not exceed the limit, or all settlements regardless of direction and business status
3. WHEN multiple search criteria are applied, THE Payment_Limit_Monitoring_System SHALL return settlements that match all specified criteria
4. WHEN displaying search results, THE Payment_Limit_Monitoring_System SHALL show settlement details including direction, type, and business status, current status, and group subtotal information calculated from PAY settlements with business status not CANCELLED, while displaying all settlements regardless of direction and business status in the results
5. THE Payment_Limit_Monitoring_System SHALL provide search results in a paginated format for efficient browsing of large result sets
6. WHEN users want to export search results, THE Payment_Limit_Monitoring_System SHALL allow downloading the filtered settlements as an Excel file containing all relevant settlement details and status information for settlements of all directions and business statuses
7. WHEN displaying the user interface, THE Payment_Limit_Monitoring_System SHALL show settlement groups in the upper section and individual settlements in the lower section, including settlements of all directions and business statuses for complete visibility
8. WHEN a user clicks on a settlement group in the upper section, THE Payment_Limit_Monitoring_System SHALL display all settlements belonging to that group in the lower section with their individual details, statuses, direction, and business status indicators
9. WHEN displaying settlement information, THE Payment_Limit_Monitoring_System SHALL show sufficient context including settlement direction, type, and business status, group subtotal in USD calculated from PAY settlements with business status not CANCELLED, exposure limit, current exchange rates as reference for currency conversion, and filtering rule application status to explain why PAY settlements are BLOCKED or not BLOCKED

### Requirement 7

**User Story:** As an external system, I want to query settlement status via API, so that I can make informed processing decisions based on current settlement approval status.

#### Acceptance Criteria

1. WHEN an external system queries by Settlement_ID, THE Payment_Limit_Monitoring_System SHALL return the current settlement status (CREATED, BLOCKED, PENDING_AUTHORISE, or AUTHORISED) along with settlement direction, type, and business status information for all settlements
2. WHEN a settlement status is BLOCKED, THE Payment_Limit_Monitoring_System SHALL include detailed information explaining why the PAY settlement is blocked, including group subtotal calculated from PAY settlements with business status not CANCELLED, exposure limit, and affected counterparty details
3. WHEN a settlement status is PENDING_AUTHORISE or AUTHORISED, THE Payment_Limit_Monitoring_System SHALL include approval workflow information including timestamps and user actions taken
4. WHEN a Settlement_ID is not found, THE Payment_Limit_Monitoring_System SHALL return an appropriate error response with clear messaging
5. THE Payment_Limit_Monitoring_System SHALL provide API responses in a structured format with sufficient detail to prevent follow-up queries for clarification
6. WHEN a settlement status changes to AUTHORISED, THE Payment_Limit_Monitoring_System SHALL send a notification to external systems containing the Settlement_ID and authorization details to trigger downstream processing
7. WHEN a manual recalculation is requested via API endpoint with scope criteria (PTS, Processing_Entity, from_Value_Date), THE Payment_Limit_Monitoring_System SHALL recalculate subtotals from PAY settlements with business status not CANCELLED and update settlement statuses for all settlements matching the specified criteria
8. WHEN performing manual recalculation, THE Payment_Limit_Monitoring_System SHALL apply current filtering rules and exposure limits to determine updated settlement statuses within the specified scope

### Requirement 8

**User Story:** As a system administrator, I want to configure exposure limits and currency conversion rates, so that the system can adapt to changing business requirements and market conditions.

#### Acceptance Criteria

1. WHEN operating in MVP mode, THE Payment_Limit_Monitoring_System SHALL use a fixed Exposure_Limit of 500 million USD for all counterparties
2. WHEN configured for advanced mode, THE Payment_Limit_Monitoring_System SHALL fetch counterparty-specific exposure limits from an external system daily and apply the appropriate limit based on the settlement's Counterparty_ID
3. THE Payment_Limit_Monitoring_System SHALL automatically fetch and store exchange rates from external systems daily for currency conversion
4. WHEN currency conversion is required, THE Payment_Limit_Monitoring_System SHALL use the latest available exchange rate at the time of settlement processing to convert amounts to USD equivalent
5. WHEN new exchange rates are fetched and stored, THE Payment_Limit_Monitoring_System SHALL make them available for future settlement processing without recalculating existing subtotals
6. WHEN counterparty-specific limits are updated, THE Payment_Limit_Monitoring_System SHALL re-evaluate all affected settlement groups against their respective new limits immediately without requiring mass database updates
7. WHEN exposure limits are changed frequently for operational or risk management purposes, THE Payment_Limit_Monitoring_System SHALL handle limit updates efficiently without performance degradation or lengthy recalculation processes
8. THE Payment_Limit_Monitoring_System SHALL log all configuration changes and limit updates with timestamps and system identity

### Requirement 10

**User Story:** As a system architect, I want the system to handle distributed processing correctly, so that settlement data remains consistent across multiple system instances and concurrent operations.

#### Acceptance Criteria

1. WHEN the system operates with multiple instances behind a load balancer, THE Payment_Limit_Monitoring_System SHALL ensure that settlement processing and subtotal calculations remain consistent across all instances
2. WHEN settlement versions arrive out of chronological order due to network delays or processing differences, THE Payment_Limit_Monitoring_System SHALL apply only the latest version based on Settlement_Version number to subtotal calculations
3. WHEN multiple settlements within the same group are processed concurrently by different system instances, THE Payment_Limit_Monitoring_System SHALL use appropriate locking or atomic operations to prevent subtotal calculation race conditions
4. WHEN a settlement version update conflicts with concurrent processing, THE Payment_Limit_Monitoring_System SHALL ensure data consistency through proper transaction isolation or retry mechanisms
5. WHEN system instances restart or fail during settlement processing, THE Payment_Limit_Monitoring_System SHALL maintain data integrity and resume processing without data loss or corruption
6. THE Payment_Limit_Monitoring_System SHALL provide idempotent settlement processing to handle duplicate settlement submissions without creating inconsistent state

### Requirement 11

**User Story:** As a risk manager, I want to update exposure limits frequently without system performance impact, so that I can respond quickly to changing market conditions and risk profiles.

#### Acceptance Criteria

1. WHEN an exposure limit is updated for any counterparty, THE Payment_Limit_Monitoring_System SHALL apply the new limit to all settlement status calculations immediately without requiring database record updates
2. WHEN a limit change affects thousands of settlements, THE Payment_Limit_Monitoring_System SHALL complete the limit update operation within 5 seconds regardless of the number of affected settlements
3. WHEN multiple limit updates occur within a short time period, THE Payment_Limit_Monitoring_System SHALL handle concurrent limit changes without performance degradation
4. WHEN a limit is updated during peak processing periods, THE Payment_Limit_Monitoring_System SHALL maintain normal query response times for settlement status requests
5. THE Payment_Limit_Monitoring_System SHALL provide immediate feedback to users when settlement statuses change due to limit updates, without requiring manual refresh or recalculation requests

#### Example Scenario

**Initial State:**
- Settlement Group ABC (PTS1, PE1, Counterparty_X, 2024-01-15) has subtotal = 480M USD
- Current exposure limit for Counterparty_X = 500M USD  
- Group contains 10,000 individual settlements
- All 10,000 settlements currently have status CREATED (within limit)

**Limit Update Event:**
- Risk manager updates Counterparty_X limit from 500M USD to 450M USD
- New condition: 480M USD > 450M USD (group now exceeds limit)

**System Response (Required):**
- Limit update completes within 5 seconds
- Next status query for any settlement in Group ABC returns BLOCKED
- No database record updates required for the 10,000 settlements
- All settlement status queries reflect new limit immediately
- System maintains normal response times during the update

**Performance Comparison:**
- Traditional approach: Update 10,000 settlement records (30-60 seconds)
- Required approach: Update limit configuration only (< 5 seconds)

### Requirement 9

**User Story:** As a compliance officer, I want to access historical settlement data and review decisions, so that I can perform audits and ensure regulatory compliance.

#### Acceptance Criteria

1. WHEN querying historical data, THE Payment_Limit_Monitoring_System SHALL provide access to all settlement versions and their timestamps, including settlements of all directions, types, and business statuses with their complete information
2. WHEN reviewing audit trails, THE Payment_Limit_Monitoring_System SHALL display all review actions, approvals, and system calculations with full traceability for settlements of all directions and business statuses
3. WHEN generating reports, THE Payment_Limit_Monitoring_System SHALL export settlement data including direction, type, and business status, subtotals, and review status in standard formats, showing settlements of all directions and business statuses for complete transaction visibility
4. WHEN data retention policies apply, THE Payment_Limit_Monitoring_System SHALL archive historical data while maintaining accessibility for compliance periods
5. THE Payment_Limit_Monitoring_System SHALL ensure data integrity and prevent unauthorized modifications to historical records