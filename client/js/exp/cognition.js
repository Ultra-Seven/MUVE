import Connector from "../engine/connector";
import Gradient from "javascript-color-gradient";
import _ from "underscore";
import HumanNames from "human-names";

class Cognition {
    constructor(urlParams, engine, config) {
        $("#result_body").show();
        this.cognitionStart = 0;
        this.cognitionEnd = 0;
        this.user = urlParams.get('user');
        this.level = parseInt(urlParams.get('level'));
        this.position = parseInt(urlParams.get('position'));
        this.colorPos = parseInt(urlParams.get('colorPos'));
        this.nrQueries = parseInt(urlParams.get('nrQueries'));
        this.nrPlots = parseInt(urlParams.get('nrPlots'));
        const integerQueries = Math.max(Math.floor(this.nrQueries / this.nrPlots), 1);
        this.extraQueries = this.nrQueries - integerQueries * this.nrPlots;

        this.row = 2;
        // Color
        const colorGradient = new Gradient();
        const color1 = "#3F2CAF";
        const color2 = "#e9446a";
        const color3 = "#edc988";
        const color4 = "#607D8B";
        colorGradient.setGradient(color1, color2, color3, color4);
        colorGradient.setMidpoint(this.nrQueries);
        this.colorGradient = colorGradient;

        $("#datasets").prop('disabled', 'disabled');
        $("#planner").prop("disabled", 'disabled');

        $("#btn-start-recording").hide();
        $("#btn-dev").html("Go back");
        $("#btn-dev").click(() => {
            window.history.back();
        });
        $("#btn-submit").hide();
        $("#spoken-text").prop("disabled", true);
        $("#spoken-text").css("color","black");
        $("#spoken-text").css('font-weight', 'bold');

        this.draw(this.nrQueries);

        // Simulate click the submit button
        this.engine = engine;
        const params = {
            sender: "AJAX",
            callback: () => {
                alert("You have finished the task!");
            },
            url: config.host + "/cognition"
        };
        this.querySender = new Connector(params);
        this.createModal();
    }

    draw(nrQueries) {
        $("#viz").empty();
        $("#legend").empty();
        // Build Queries
        const nrPlots = this.nrPlots;
        const nrQueriesInPlot = Math.floor(nrQueries / nrPlots);
        const plotsList = this.generatePlots(nrQueriesInPlot);

        const plots = [];
        // Initialize all plots
        for (let plotCtr = 0; plotCtr < plotsList.length; plotCtr++) {
            const columnPlot = {};
            const literalPlot = {};
            const literals = plotsList[plotCtr]["literals"];
            const columns = plotsList[plotCtr]["columns"];

            columnPlot["data"] = _.map(columns, literal => {
                return {label: literal, y: _.random(50, 100)}
            });

            literalPlot["data"] = _.map(literals, column => {
                return {label: column, y: _.random(50, 100)}
            });
            columnPlot["dataID"] = plotCtr + "";
            literalPlot["dataID"] = plotCtr + "";
            columnPlot["type"] = "column";
            literalPlot["type"] = "value";
            plots.push(columnPlot);
            plots.push(literalPlot);
        }

        const samplePlots = _.sample(plots, nrPlots);
        let restPlots = _.difference(plots, samplePlots);
        const firstRowPlots = Math.floor(nrPlots / this.row);
        const secondRowPlots = nrPlots - firstRowPlots;
        const nrFigures = [firstRowPlots, secondRowPlots];

        let start = 0;
        let priorQueries = 0;
        const predicates = [];
        const targetInPlot = {};
        let targetIndex;
        const dataIDGroups = _.groupBy(samplePlots, plot => plot["dataID"]);
        for (let rowCtr = 0; rowCtr < this.row; rowCtr++) {
            const rowName = "row_" + rowCtr;
            $("#viz").append("<div id='" + rowName + "'></div>");
            const nrFigs = nrFigures[rowCtr];
            for (let figureCtr = 0; figureCtr < nrFigs; figureCtr++) {
                const barName = "bar_" + rowCtr + "_" + figureCtr;
                const width = Math.floor(90.0 / nrFigs);
                let data = samplePlots[start + figureCtr]["data"];
                const lastPlot = (start + figureCtr) === (nrPlots - 1);
                const currentNrQueries = lastPlot ? nrQueriesInPlot + this.extraQueries : nrQueriesInPlot;
                $("#" + rowName).append(
                    "<div id='" + barName +
                    "' style='width: " + width + "%; height: 280px;display: inline-block;'></div>"
                );
                // Generate predicate
                const dataID = samplePlots[start + figureCtr]["dataID"];
                const type = samplePlots[start + figureCtr]["type"];
                if (!(dataID in targetInPlot)) {
                    targetInPlot[dataID] = {};
                    targetInPlot[dataID]["nrQueries"] = 0;
                }

                // Target query is in the plot
                let queryIndex;
                const singlePlot = dataIDGroups[dataID].length === 1;
                let inPlot = false;
                if (priorQueries <= this.position && priorQueries + currentNrQueries > this.position) {
                    queryIndex = this.position - priorQueries;
                    inPlot = true;
                    targetIndex = queryIndex;
                    this.targetValue = data[queryIndex]["y"];
                }
                else {
                    queryIndex = _.random(0, currentNrQueries);
                }
                const query = data[queryIndex];
                // Delete the target query
                if (!inPlot) {
                    data.splice(queryIndex, 1);
                }
                data = data.slice(0, currentNrQueries);
                samplePlots[start + figureCtr]["data"] = data;
                targetInPlot[dataID][type] = query["label"];

                if (singlePlot) {
                    const alternativePlot = _.find(restPlots, restPlot => restPlot["dataID"] === dataID);
                    const alterQuery = _.sample(alternativePlot["data"]);
                    targetInPlot[dataID]["nrQueries"] += currentNrQueries;
                    targetInPlot[dataID][alternativePlot["type"]] = alterQuery["label"];
                    restPlots = _.without(restPlots, alternativePlot);
                }

                if (targetInPlot[dataID]["nrQueries"] > 0) {
                    const predicate = "\"" + targetInPlot[dataID]["column"] + "\" = ["
                        + targetInPlot[dataID]["value"] + "]";
                    predicates.push(predicate);
                }
                targetInPlot[dataID]["nrQueries"] += currentNrQueries;
                priorQueries += currentNrQueries;
            }
            start += nrFigs;
        }

        const fixedPlot = {};

        // Add predicates from the rest plots
        _.each(restPlots, restPlot => {
            const dataID = restPlot["dataID"];
            const sampleQuery = _.sample(restPlot["data"]);
            if (!(dataID in fixedPlot)) {
                fixedPlot[dataID] = {};
                fixedPlot[dataID]["nrQueries"] = 0;
            }
            fixedPlot[dataID][restPlot["type"]] = sampleQuery["label"];
            if (fixedPlot[dataID]["nrQueries"] > 0) {
                const predicate = "\"" + fixedPlot[dataID]["column"] + "\" = ["
                    + fixedPlot[dataID]["value"] + "]";
                predicates.unshift(predicate);
            }
            fixedPlot[dataID]["nrQueries"]++;
        });


        // Assign color ranks
        if (this.colorPos >= 0) {
            const ranks = _.shuffle(_.range(0, nrQueries, 1));
            const pos = _.indexOf(ranks, this.colorPos);
            const targetRank = ranks[this.position];
            ranks[this.position] = this.colorPos;
            ranks[pos] = targetRank;
            _.each(samplePlots, (plot, idx) => {
                _.each(plot["data"], (dataPoint, qid) => {
                    dataPoint["rank"] = ranks[idx * nrQueriesInPlot + qid];
                });
            });
        }

        start = 0;
        for (let rowCtr = 0; rowCtr < this.row; rowCtr++) {
            const nrFigs = nrFigures[rowCtr];
            for (let figureCtr = 0; figureCtr < nrFigs; figureCtr++) {
                const barName = "bar_" + rowCtr + "_" + figureCtr;
                let plot = samplePlots[start + figureCtr];
                const dataID = plot["dataID"];
                const type = plot["type"];
                const titlePredicates = [];
                _.each(_.keys(fixedPlot), plotDataID => {
                    const predicate = "\"" + fixedPlot[plotDataID]["column"] + "\" = ["
                        + fixedPlot[plotDataID]["value"] + "]";
                    titlePredicates.push(predicate);
                });
                _.each(_.keys(targetInPlot), plotDataID => {
                    if (dataID !== plotDataID) {
                        const predicate = "\"" + targetInPlot[plotDataID]["column"] + "\" = ["
                            + targetInPlot[plotDataID]["value"] + "]";
                        titlePredicates.push(predicate);
                    }
                });
                const freeTitle = {column: targetInPlot[dataID]["column"], value: targetInPlot[dataID]["value"]};
                freeTitle[type] = "?";
                titlePredicates.push("\"" + freeTitle["column"] + "\" = ["
                    + freeTitle["value"] + "]");
                plot["title"] = titlePredicates.join(" and ");
                this.drawBarChart(barName, plot);
            }
            start += nrFigs;
        }

        // Write the target query
        $("#spoken-text").val(predicates.join(" and "));

        // Color gradients
        if (this.colorPos >= 0) {
            const scales = this.colorGradient.getArray();
            const width = Math.floor(90.0 / this.nrQueries);
            _.each(scales, (color, idx) => {
                const id = "color_" + idx;
                $("#legend").append("<button class='ui button' id='" + id +"'></button>");
                $("#" + id).css("background-color", color);
                $("#" + id).width("10px");
                $("#" + id).height("30px");
            });
            $("#color_0").html("High").css('color', 'white').css("fontSize", 14);
            $("#color_" + (scales.length - 1)).html("Low").css('color', 'white').css("fontSize", 14);
        }
        // Remove watermarks
        $('.canvasjs-chart-credit').remove();
    }

    drawBarChart(container, plot) {
        let render_data = _.map(plot["data"], dataPoint => {
            const rank = dataPoint["rank"];
            const color = rank !== undefined ? this.colorGradient.getColor(rank + 1) : "#0000ff";

            return {
                y: dataPoint["y"],
                label: dataPoint["label"],
                color: color,
                click: (e) => {
                    if (this.cognitionEnd === 0) {
                        this.isMatch = this.targetValue === e["dataPoint"]["y"] ? 1 : 0;
                        this.cognitionEnd = Date.now();
                        alert("Timer stops! You spent " + (this.cognitionEnd - this.cognitionStart)
                            + "ms to find the result. Please submit it! If you click the bar accidentally, " +
                            "please refresh the page.");
                    }
                }
            }
        });

        const title = plot["title"];

        $("#" + container).CanvasJSChart({
            animationEnabled: true,
            title: {
                text: title,
                fontFamily: "Calibri, Optima, Candara, Verdana, Geneva, sans-serif"
                // fontColor: fontColor
            },
            data: [{
                type: "column",
                indexLabel: "{y}",
                indexLabelPlacement: "outside",
                indexLabelOrientation: "horizontal",
                dataPoints: render_data
            }],
            axisX: {
                labelMaxWidth: 100,
                interval: 1
            },
        });
    }

    createModal() {
        const imagePath = this.colorPos >= 0 ? "/images/color.png" : "/images/overview.png";
        $("#body").append(
            `
               <div class="ui large modal" id="target_query">
                    <div class="header">
                       Please find the bar that matches with the Text Query
                    </div>
                    <div class="image content">
                        <div class="ui massive image">
                          <img src="` + imagePath + `">
                        </div>
                        
                    </div>
                        
                    <div>
                        <div class="description" style="padding-left: 100px">
                          <p>
                                The example of multiplots is shown above.
                                Please find the bar and associated plot that match with the text query. 
                                When you find it, please click the bar to terminate the timer 
                                and submit the value of bar in the top box!<br>
                                <span style="font-size: 20px; font-weight: bold;">Target Text Query: </span>
                                <span style="color:red; font-size: 20px; font-weight: bold;">`
                                + $("#spoken-text").val() + `</span> <br>
                                The goal of study is to evaluate the cognition time of visualizations. 
                                So we hope you to read the text query before looking through plots. 
                                When you are ready, please click the green button to start a timer.
                          </p>
                        </div>
                    </div>
                    <div class="actions">
                        <div class="ui positive right labeled icon button" id="timer_start">
                            Yep, go to plots
                            <i class="checkmark icon"></i>
                        </div>
                    </div>
               </div>
            `);
        $("#timer_start").click(() => {
            if (this.cognitionStart === 0) {
                alert("Timer starts! If you click the button accidentally, please " +
                    "refresh the page.");
                this.cognitionStart = Date.now();
            }
        });
        $("#target_query")
            .modal('show');
    }


    generatePlots(nrQueriesInPlot) {
        // Human names
        const names = [];
        const nameColumns = ["worker", "student", "employer",
            "professor", "doctor", "driver", "farmer", "manager",
            "nickname", "account", "father", "mother"];
        let name;
        while(names.length < nrQueriesInPlot + 1 + this.extraQueries) {
            name = HumanNames.allRandom();
            while (_.contains(names, name)) {
                name = HumanNames.allRandom();
            }
            names.push(name);
        }
        const namesColumnsSample = _.sample(nameColumns, nrQueriesInPlot + 1 + this.extraQueries);

        // Cities
        const cities = ["NYC", "LA", "Chicago", "Houston", "Phoenix", "Philadelphia", "San Antonio",
        "San Diego", "Dallas", "San Jose", "Austin", "Columbus", "Charlotte", "Seattle", "Portland",
        "Detroit", "Tucson", "Fresno", "Miami", "Henderson", "Plano", "Irvine", "Glendale", "Garland", "Syracuse"];
        const cityColumns = ["departure", "destination", "hometown",
            "city", "borough", "live", "travel", "birth", "location", "park", "visit", "place"];
        const citiesSample = _.sample(cities, nrQueriesInPlot + 1 + this.extraQueries);
        const citiesColumnsSample = _.sample(cityColumns, nrQueriesInPlot + 1 + this.extraQueries);

        // Date
        const months = ["Jan", "Feb", "Mar", "Apr", "May", "June",
            "July", "Aug", "Sept", "Oct", "Nov", "Dec"];
        const datesColumns = ["action", "paid", "filing",
            "assigned", "approved", "declined", "proposed", "month", "time", "admitted", "graduated", "hired"];

        const monthsSample = _.map(
            _.sortBy(_.sample(_.range(months.length), nrQueriesInPlot + 1 + this.extraQueries)),
                idx => months[idx]
        );
        const monthColumnsSample = _.sample(datesColumns, nrQueriesInPlot + 1 + this.extraQueries);

        return [
            {literals: _.sortBy(names), columns: _.sortBy(namesColumnsSample)},
            {literals: _.sortBy(citiesSample), columns: _.sortBy(citiesColumnsSample)},
            {literals: monthsSample, columns: _.sortBy(monthColumnsSample)}
        ]
    }

    send(results) {
        const respond = this.cognitionEnd - this.cognitionStart;
        console.log("isMatch: " + this.isMatch);
        if (isNaN(respond) || this.cognitionEnd === 0) {
            return;
        }
        const message = [this.user, this.level, this.position,
            this.colorPos, this.nrQueries,
            this.nrPlots,
            respond, this.isMatch];
        this.querySender.send(message.join("|"));
    }
}
export default Cognition;