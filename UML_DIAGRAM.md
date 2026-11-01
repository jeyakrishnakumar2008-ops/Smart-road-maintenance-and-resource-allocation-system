# UML DIAGRAM — Smart Municipal Road Maintenance

## Class Diagram (Mermaid)

```mermaid
classDiagram

    class Equipment {
        -String name
        +Equipment(name)
        +getName() String
        +equals(Object) boolean
        +hashCode() int
        +toString() String
    }

    class RoadDamage {
        -String reportId
        -String locationId
        -String damageType
        -int severityRank
        -int accidentRiskRank
        -int roadImportanceRank
        -int trafficRank
        -int reportOrder
        -String requiredEquipment
        -String status
        -String assignedTeamId
        +assignTeam(teamId)
        +markCompleted()
        +updateRanks(sev, acc, imp, trf) boolean
        +isValidRank(rank) boolean
    }

    class MaintenanceTeam {
        -String teamId
        -String teamName
        -String currentLocationId
        -boolean isAvailable
        -HashSet~Equipment~ equipmentSet
        -String currentAssignmentId
        +addEquipment(Equipment)
        +hasEquipmentByName(String) boolean
        +assignJob(reportId)
        +completeRepair(newLocationId)
        +getEquipmentNames() String
    }

    class RepairJob {
        <<abstract>>
        -String jobId
        -String reportId
        -String locationId
        +requiredEquipment() HashSet~Equipment~
        +getRepairDescription() String
    }

    class PotholeRepair {
        +requiredEquipment() HashSet~Equipment~
        +getRepairDescription() String
    }

    class ResurfacingRepair {
        +requiredEquipment() HashSet~Equipment~
        +getRepairDescription() String
    }

    class RoadConnection {
        -String destinationId
        -int travelTime
        -boolean isOpen
        +close()
        +reopen()
    }

    class RoadNetwork {
        -HashMap~String, List~RoadConnection~~ adjacencyList
        -HashSet~String~ locations
        -HashMap~String, String~ locationNames
        +addLocation(id, name)
        +addRoad(src, dst, time)
        +addBidirectionalRoad(loc1, loc2, time)
        +closeRoad(src, dst) boolean
        +reopenRoad(src, dst) boolean
        +findShortestPath(start, end) DijkstraResult
    }

    class RepairPriorityPolicy {
        +compare(RoadDamage, RoadDamage) int
    }

    class DijkstraResult {
        -boolean reachable
        -int totalTime
        -List~String~ path
        +isReachable() boolean
        +getTotalTime() int
        +getPath() List~String~
        +getPathString() String
        +unreachable() DijkstraResult
    }

    class AssignmentResult {
        -String outcome
        -String reportId
        -String teamId
        -DijkstraResult routeResult
        -String failureReason
        -List~String~ skippedTeams
        +isSuccess() boolean
        +success(reportId, teamId, route) AssignmentResult
        +noPending() AssignmentResult
        +noEligibleTeam(reportId, reason, skipped) AssignmentResult
    }

    class MunicipalService {
        -HashMap~String, RoadDamage~ reportsMap
        -HashMap~String, MaintenanceTeam~ teamsMap
        -PriorityQueue~RoadDamage~ pendingQueue
        -RoadNetwork network
        -int nextReportOrder
        +addNewReport(...) String
        +updateReport(...) String
        +assignNextPendingRepair() AssignmentResult
        +completeRepair(reportId) String
        +closeRoad(src, dst) boolean
        +reopenRoad(src, dst) boolean
        +getPendingReportsOrdered() List~RoadDamage~
        +getTotalReports() int
        +getPendingCount() int
    }

    class HtmlHelper {
        +escape(String) String
        +header(title) String
        +footer() String
        +pageHeading(title, subtitle) String
        +alertSuccess(msg) String
        +alertError(msg) String
        +statusBadge(status) String
        +cardOpen(title) String
        +cardClose() String
    }

    class WebServer {
        -MunicipalService service
        -int port
        +start()
    }

    class Main {
        +main(String[])
    }

    %% Inheritance (IS-A)
    RepairJob <|-- PotholeRepair : extends
    RepairJob <|-- ResurfacingRepair : extends

    %% Composition (HAS-A)
    MaintenanceTeam "1" *-- "many" Equipment : contains

    %% Associations
    MunicipalService "1" *-- "many" RoadDamage : manages
    MunicipalService "1" *-- "many" MaintenanceTeam : manages
    MunicipalService "1" *-- "1" RoadNetwork : uses
    MunicipalService "1" *-- "1" RepairPriorityPolicy : uses

    RoadNetwork "1" *-- "many" RoadConnection : contains

    WebServer "1" --> "1" MunicipalService : uses
    WebServer "1" --> "1" HtmlHelper : uses

    Main "1" --> "1" MunicipalService : creates
    Main "1" --> "1" WebServer : creates

    AssignmentResult "1" --> "1" DijkstraResult : contains
```

## Relationships Summary

| Relationship | Type | Description |
|---|---|---|
| `PotholeRepair` → `RepairJob` | Inheritance | PotholeRepair IS-A RepairJob |
| `ResurfacingRepair` → `RepairJob` | Inheritance | ResurfacingRepair IS-A RepairJob |
| `MaintenanceTeam` → `Equipment` | Composition | A team HAS-A set of equipment |
| `MunicipalService` → `RoadDamage` | Aggregation | Service manages many damage reports |
| `MunicipalService` → `MaintenanceTeam` | Aggregation | Service manages 3 teams |
| `MunicipalService` → `RoadNetwork` | Aggregation | Service uses the road graph |
| `WebServer` → `MunicipalService` | Dependency | Server calls service methods |
| `RoadNetwork` → `RoadConnection` | Composition | Network contains road edges |
