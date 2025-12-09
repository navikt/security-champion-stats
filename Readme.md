# Security Champion Stats

For oppfølging av Security Champions Stats.

## Hva gjør den?

## Hvordan kjører den?


### Flyt ([mermaid](https://github.blog/2022-02-14-include-diagrams-markdown-files-mermaid/) -syntaks)

```mermaid
sequenceDiagram
    participant B as SecurityChampion-backend
    participant F as SecurityChampion-frontend
    participant db
    participant T as Microsoft Teams
    participant TK as Teamkatalogen
    participant Slack
    
    B-->>TK: Get all teams, areas, etc.
    TK-->>B: [team, team, …]
    B-->>db: Update db with new members
    B-->>F: Show updated data
    
    B-->>db: Fetch members
    db-->>B: SC members
    B-->>SLACK: Get SC activity in slack
    SLACK-->> B: [SC activity]
    B-->>db: Update members with activity
    B-->>F: Show updated data
    
    B-->>T: Get members report for meeting
    T-->> B: [Report]
    B-->>db: Fetch members
    db-->>B: SC members
    B-->>db: Update members with activity and potentially new members
    B-->>F: Show updated data
   
    loop paginated
        B->>B: Aggregate data, match data and update data in db
    end
```

### Konfigurasjon
No data for now
