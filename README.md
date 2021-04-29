# MUVE (Multiplots for Voice quEries)
We propose a a system for robust voice querying by using multiplots to show results of aggregated query. Phonetically similar queries are chosen as candidates that are filled the screen with multiplots. 

## Dependencies
For most of dependencies, please refer to Maven dependency file and install them by `mvn install`. Besides, please install Gurobi version 9 to enable the ILP planner.

## Indexes
We built Phonetically Similar Indexes by Apache Lucene. The indexes of different data sets are stored under `./phonetic` directory.

## Planner Experiments
`PlannerBenchmark.java` (under src/main/java/benchmarks/PlannerBenchmark) runs planner experiments in the paper. When you execute the experiment program, please add following arguments in the command `[exp choice] [planner choice] [dataset]`

- exp choice: 0: vary number of queries, 1: vary resolution on the screen, 2: vary number of rows on the screen, 3: vary weight of processing cost, 4: evaluate the effect of merging queries together, 5: varying upper bound of processing cost.
- planner choice: 0: ILP planner, 1: greedy planner without final processing, 2: greedy planner with final processing
- dataset: sample_311: 311 request data, sample_au: advertisement data, dob_job: DOB data.

## Online Demo
We hope to publish our online demo ASAP
