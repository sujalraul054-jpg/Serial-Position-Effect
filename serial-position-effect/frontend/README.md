# Serial Position Effect Experiment

A web application for comparing free recall of two ten-word lists:

- **Normal list**: ten unrelated words.
- **Semantic list**: unrelated words at positions 1-3 and 8-10, and four related
  words (for example table, wood, furniture, chair) at positions 4-7.

Each participant completes both lists in one session. Results are scored on the
backend, stored in a database, and exported to a spreadsheet for analysis.

## Procedure

1. The participant starts a session. The server builds both word lists and decides
   which one is shown first.
2. Level 1: ten words appear one at a time. The participant types the words they
   remember.
3. Level 2: the same, with the other list.
4. The session is submitted as one unit, scored, and stored. Nothing is stored
   if the session is abandoned before the end.
5. The results page compares the two lists and shows the recall improvement.
   The spreadsheet can be downloaded from the results page or from Settings.
   There is no option to repeat the session: each participant takes part once.
   The next participant starts from a fresh page load.

The participant is not told which list is the semantic one until the results page.

### Counterbalancing

The first list alternates with the participant number: odd numbers (P001, P003, ...)
see the normal list first, even numbers see the semantic list first. Over an even
number of participants both orders are used equally often.

### How the word lists are built

Words are grouped into 17 themes (`WordBank.java`). The normal list takes one word
from each of ten different themes. The semantic list takes four words from one
theme for positions 4-7, and one word from each of six other themes for the
remaining positions. The two lists of a session use disjoint themes, so no word
or theme appears in both. The themes are hand-curated: they keep related words
out of the unrelated positions, but they are not a formal measure of semantic
distance.

## Recall improvement

```
total recall improvement (%) = ((semantic total recalled - normal total recalled) / normal total recalled) x 100
middle recall improvement (%) = ((semantic middle recalled - normal middle recalled) / normal middle recalled) x 100
```

Recall is the number of words recalled (out of 10) in each list. A positive value
means more words were recalled from the semantic list. If normal recall is 0 the
ratio is undefined and the result is shown as `N/A` on the website and `n/a` in
the spreadsheet. Middle recall counts correctly recalled words at positions 4-7
out of four; its percentage is that count divided by four, multiplied by 100.
The results page shows both middle counts and percentages for each test.

## Spreadsheet

`GET /api/experiment/export` returns an `.xlsx` file with one `Results` worksheet.

The worksheet has one row per test, so two rows per participant. Its first 14
columns preserve the raw test data:

| Column | Content |
|---|---|
| Participant ID | `P001`, `P002`, ... |
| Test Type | `Normal` or `Semantic` |
| Test Order | `Normal first` or `Semantic first`: which test this participant was given first |
| Total Recalled | Number of the 10 words recalled |
| Position 1 ... Position 10 | `1` if the word at that position was recalled, `0` if not |

A word counts as recalled if it appears anywhere in the participant's answer,
ignoring case and surrounding spaces. Order of recall is not scored. After
Position 10, the worksheet has three columns: total recall improvement, middle
recall (Normal and Semantic counts and percentages in one cell), and middle
position recall improvement. Participant IDs are vertically merged across each
participant's two test rows. Middle recall is shown in each test's own row; the
Normal row shows the 0.0% total-improvement baseline, and the Semantic row shows
the calculated total improvement. Middle-position improvement is one live Excel
formula in a cell merged across both test rows. A final mean row summarizes
participants.

The file is generated from the database on every download, so it always contains
every recorded session and does not conflict with a copy open in Excel.

## Deleting data

Settings has two options. Both remove data from the database, and therefore from
the spreadsheet and the graph, which are built from it.

- **Delete previous attempt** deletes the most recent session. The next session
  reuses that participant number and so keeps the same test order, which keeps the
  alternation balanced when a session has to be discarded.
- **Delete all recorded sessions** empties the database. Numbering restarts at P001.

## Scoring

- Primacy: positions 1-3. Middle: positions 4-7. Recency: positions 8-10.
- Duplicate words are counted once and reported on the results page.
- Words that are not on the list are ignored.

## Requirements

- Java 17 or later and Maven
- A browser, and a way to serve `frontend/` over `http://` (for example the VS Code
  Live Server extension). Opening `index.html` directly from disk is blocked by
  the backend's CORS rules.

## Running

Backend:

```
cd backend/springboot
mvn spring-boot:run
```

It listens on `http://localhost:8080`. The H2 database is stored in
`backend/springboot/data/`. Its console is at `http://localhost:8080/h2-console`
with JDBC URL `jdbc:h2:file:./data/serialpositiondb`, user `sa`, empty password.

Frontend: serve the `frontend/` folder and open `index.html` through that server.

## API

| Method and path | Purpose |
|---|---|
| `GET /api/experiment/plan` | Word lists for a new session, in presentation order |
| `POST /api/experiment/sessions` | Score and store a completed session (both tests) |
| `DELETE /api/experiment/sessions/latest` | Delete the most recent session |
| `DELETE /api/experiment/sessions` | Delete all recorded sessions |
| `GET /api/experiment/aggregate` | Recall percentage per position across participants, per test type |
| `GET /api/experiment/export` | The spreadsheet |

The submitted word lists are checked against the rules above, so a request cannot
store a list the experiment did not generate.

## Project structure

```
frontend/                      index.html, style.css, script.js
backend/springboot/
  pom.xml
  src/main/java/com/example/serialposition/
    controller/                ExperimentController, ResultsController
    service/                   WordBank, ExperimentPlanService, ScoringService,
                               SessionService, AggregationService, SpreadsheetExportService
    model/                     Participant, TestResult, PositionRecall, TestType
    repository/                Spring Data repositories
    dto/                       request and response records
    config/, exception/        CORS, error handling
  src/main/resources/application.properties
```

## Moving to MySQL

Replace the H2 dependency with the MySQL driver, change the `spring.datasource.*`
properties, and replace `spring.jpa.hibernate.ddl-auto=update` with a migration
tool such as Flyway. No Java code depends on H2.
