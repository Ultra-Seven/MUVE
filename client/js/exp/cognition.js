import Connector from "../engine/connector";
import Gradient from "javascript-color-gradient";
import _ from "underscore";
import HumanNames from "human-names";

class Cognition {
    constructor(urlParams, engine, config) {
        $("#information").hide();
        document.getElementById('recording').style.width = '90%';
        this.cognitionStart = 0;
        this.cognitionEnd = 0;
        this.user = urlParams.get('user');
        this.level = parseInt(urlParams.get('level'));
        this.position = parseInt(urlParams.get('position'));
        this.colorPos = parseInt(urlParams.get('colorPos'));
        this.nrQueries = parseInt(urlParams.get('nrQueries'));
        this.nrPlots = parseInt(urlParams.get('nrPlots'));
        this.nrPreds = parseInt(urlParams.get('nrPreds') || "2");
        this.nrTops = parseInt(urlParams.get('nrTops') || "1");
        this.nrGradients = parseInt(urlParams.get('nrGradients') || (this.nrQueries + ""));
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
        colorGradient.setMidpoint(this.nrGradients);
        this.colorGradient = colorGradient;

        $("#datasets").prop('disabled', 'disabled');
        $("#planner").prop("disabled", 'disabled');

        $("#btn-start-recording").hide();
        $("#btn-dev").hide();
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
        const plotsList = _.sample(this.generatePlots(nrQueriesInPlot), this.nrPreds);

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
        // New plots
        for (let plotCtr = plotsList.length * 2; plotCtr < nrPlots; plotCtr++) {
            const samplePlot = _.sample(plots);
            const newPlot = {};
            newPlot["data"] = _.map(samplePlot["data"], dataPoint => {
                return {label: dataPoint["label"], y: _.random(50, 100)}
            });
            newPlot["dataID"] = samplePlot["dataID"];
            newPlot["type"] = samplePlot["type"];
            plots.push(newPlot);
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
        let targetPlotIndex;
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
                    "' style='width: " + width + "%; height: 250px;display: inline-block;'></div>"
                );

                // Generate predicate
                const dataID = samplePlots[start + figureCtr]["dataID"];
                const type = samplePlots[start + figureCtr]["type"];
                if (!(dataID in targetInPlot)) {
                    targetInPlot[dataID] = {};
                    targetInPlot[dataID]["nrQueries"] = 0;
                    targetInPlot[dataID]["column"] = [];
                    targetInPlot[dataID]["value"] = [];
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
                    targetPlotIndex = start + figureCtr;
                }
                else {
                    queryIndex = _.random(0, data.length - 1);
                }
                const query = data[queryIndex];
                console.log("Plot: " + JSON.stringify(query));

                // Delete the target query
                if (!inPlot) {
                    data.splice(queryIndex, 1);
                    targetInPlot[dataID][type].push(query["label"]);
                }
                else {
                    targetInPlot[dataID][type].unshift(query["label"]);
                }
                data = data.slice(0, currentNrQueries);
                samplePlots[start + figureCtr]["data"] = data;

                // Single Plot
                if (singlePlot) {
                    const alternativePlot = _.find(restPlots, restPlot => restPlot["dataID"] === dataID);
                    const alterQuery = _.sample(alternativePlot["data"]);
                    targetInPlot[dataID]["nrQueries"] += currentNrQueries;
                    targetInPlot[dataID][alternativePlot["type"]].push(alterQuery["label"]);
                    restPlots = _.without(restPlots, alternativePlot);
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

        const dataIDOrder = {};

        for(let dataIDCtr in targetInPlot) {
            if (targetInPlot[dataIDCtr]["nrQueries"] > 0) {
                const predicate = "\"" + targetInPlot[dataIDCtr]["column"][0] + "\" = ["
                    + targetInPlot[dataIDCtr]["value"][0] + "]";
                dataIDOrder[dataIDCtr] = predicates.length;
                predicates.push(predicate);
            }
        }


        // Assign color ranks
        if (this.colorPos >= 0) {
            const ranks = _.shuffle(_.range(0, nrQueries, 1));
            const largestColor = this.nrGradients - 1;
            const pos = _.indexOf(ranks, this.colorPos);
            const targetRank = ranks[this.position];
            ranks[this.position] = this.colorPos;
            ranks[pos] = targetRank;

            const colorIndexes = _.sortBy(_.range(0, nrQueries, 1), idx => ranks[idx]);
            for (let rankCtr = 0; rankCtr < this.nrTops; rankCtr++) {
                ranks[colorIndexes[rankCtr]] = 0;
            }
            _.each(samplePlots, (plot, idx) => {
                _.each(plot["data"], (dataPoint, qid) => {
                    dataPoint["rank"] = Math.min(largestColor, ranks[idx * nrQueriesInPlot + qid]);
                });
            });
        }

        start = 0;
        const freePredicates = [];
        // Target Plot
        const targetDataID = samplePlots[targetPlotIndex]["dataID"];
        const targetType = samplePlots[targetPlotIndex]["type"];
        const targetFreeColumn = targetInPlot[targetDataID]["column"][0];
        const targetFreeValue = targetInPlot[targetDataID]["value"][0];
        const targetFreeTitleObj = {column: targetFreeColumn, value: targetFreeValue};
        targetFreeTitleObj[targetType] = "?";
        const targetFreePred = "\"" + targetFreeTitleObj["column"] + "\" = ["
            + targetFreeTitleObj["value"] + "]";
        freePredicates.push(targetFreePred);

        const charts = [];
        for (let rowCtr = 0; rowCtr < this.row; rowCtr++) {
            const nrFigs = nrFigures[rowCtr];
            for (let figureCtr = 0; figureCtr < nrFigs; figureCtr++) {
                const barName = "bar_" + rowCtr + "_" + figureCtr;
                let plot = samplePlots[start + figureCtr];
                const dataID = plot["dataID"];
                const type = plot["type"];
                const titlePredicates = [];
                const predToID = {}
                _.each(_.keys(fixedPlot), plotDataID => {
                    const predicate = "\"" + fixedPlot[plotDataID]["column"] + "\" = ["
                        + fixedPlot[plotDataID]["value"] + "]";
                    titlePredicates.push(predicate);
                    predToID[predicate] = plotDataID;
                });
                _.each(_.keys(targetInPlot), plotDataID => {
                    if (dataID !== plotDataID) {
                        const predicate = "\"" + targetInPlot[plotDataID]["column"][0] + "\" = ["
                            + targetInPlot[plotDataID]["value"][0] + "]";
                        titlePredicates.push(predicate);
                        predToID[predicate] = plotDataID;
                    }
                });

                if ((start + figureCtr) === targetPlotIndex) {
                    titlePredicates.push(targetFreePred);
                    predToID[targetFreePred] = targetDataID;
                }
                else {
                    const freeColumn = targetInPlot[dataID]["column"][0];
                    const freeValue = targetInPlot[dataID]["value"][0];
                    const freeTitleObj = {column: freeColumn, value: freeValue};
                    freeTitleObj[type] = "?";
                    const freePred = "\"" + freeTitleObj["column"] + "\" = ["
                        + freeTitleObj["value"] + "]";
                    if (_.contains(freePredicates, freePred)) {
                        const alternativeType = type === "column" ? "value" : "column";
                        const alternativeGroup = type === "column" ? "literals" : "columns";
                        freeTitleObj[alternativeType] = _.find(plotsList[parseInt(dataID)][alternativeGroup],
                            item => {
                                const newTitleObj = {};
                                newTitleObj[alternativeType] = item;
                                newTitleObj[type] = "?";
                                const newPredicate = "\"" + newTitleObj["column"] + "\" = ["
                                    + newTitleObj["value"] + "]";
                                return !_.contains(freePredicates, newPredicate);
                            });
                        const newPredicate = "\"" + freeTitleObj["column"] + "\" = ["
                            + freeTitleObj["value"] + "]";
                        titlePredicates.push(newPredicate);
                        freePredicates.push(newPredicate);
                        predToID[newPredicate] = dataID;
                    }
                    else {
                        titlePredicates.push(freePred);
                        freePredicates.push(freePred);
                        predToID[freePred] = dataID;
                    }
                }
                // Sort predicates
                const sortedTitlePredicates = _.sortBy(titlePredicates, pred => {
                    const predDataID = predToID[pred];
                    return dataIDOrder[predDataID];
                })

                plot["title"] = sortedTitlePredicates.join(" and ");
                // this.drawBarChart(barName, plot);
                const chart = this.drawUsingChartJS(barName, plot)
                charts.push(chart);
            }
            start += nrFigs;
        }

        // Write the target query
        $("#spoken-text").val(predicates.join(" and "));

        // Color gradients
        if (this.colorPos >= 0) {
            const scales = this.colorGradient.getArray();
            const width = Math.floor(90.0 / this.nrGradients);
            document.getElementById("legend").style.width = '90%';
            _.each(scales, (color, idx) => {
                const colorID = "color_" + idx;
                $("#legend").append("<button class='ui button' id='" + colorID +"'></button>");
                $("#" + colorID).css("background-color", color);
                document.getElementById(colorID).style.width = width + '%';
                $("#" + colorID).height("30px");
                $("#" + colorID).mouseover(() => {
                    this.highlightBar(charts, idx);
                });
                $("#" + colorID).mouseout(() => {
                    _.each(charts, barchart => {
                        let targetIndex = -1;
                        // reset any coloring because of selection
                        barchart.data.datasets.forEach( (dataset) => {
                            dataset.backgroundColor = _.map(dataset.backgroundColor, (color, idx) => {
                                const rank = dataset.rank[idx];
                                const newColor = rank !== undefined ? this.colorGradient.getColor(rank + 1) : "#0000ff";
                                return this.hexToRgbA(newColor, 1);
                            });
                        });
                        barchart.update();
                    });
                });
            });
            $("#color_0").html("High").css('color', 'white').css("fontSize", 14);
            $("#color_" + (scales.length - 1)).html("Low").css('color', 'white').css("fontSize", 14);
        }
        // Remove watermarks
        $('.canvasjs-chart-credit').remove();
    }

    hexToRgbA(hex, alpha){
        let c;
        if(/^#([A-Fa-f0-9]{3}){1,2}$/.test(hex)){
            c= hex.substring(1).split('');
            if(c.length === 3){
                c= [c[0], c[0], c[1], c[1], c[2], c[2]];
            }
            c = '0x'+c.join('');
            return 'rgba('+[(c>>16)&255, (c>>8)&255, c&255].join(',') + ',' + alpha + ')';
        }
        throw new Error('Bad Hex');
    }

    drawUsingChartJS(container, plot) {
        $("#" + container).append("<canvas id='canvas_" + container + "'></canvas>");
        const ctx = document.getElementById('canvas_' + container).getContext('2d');
        const data = {
            labels: _.map(plot["data"], dataPoint => dataPoint["label"]),
            datasets: [
                {
                    data: _.map(plot["data"], dataPoint => dataPoint["y"]),
                    backgroundColor: _.map(plot["data"], dataPoint => {
                        const rank = dataPoint["rank"];
                        const color = rank !== undefined ? this.colorGradient.getColor(rank + 1) : "#0000ff";
                        return this.hexToRgbA(color, 1);
                    }),
                    rank: _.map(plot["data"], dataPoint => dataPoint["rank"])
                },
            ]
        };
        const that = this;
        const options = {
            maintainAspectRatio: false,
            scales: {
                yAxes: [{
                    display: true,
                    stacked: true,
                    ticks: {
                        min: 40, // minimum value
                        max: 100 // maximum value
                    }
                }]
            },
            title: {
                display: true,
                fontSize: 20,
                text: plot["title"]
            },
            legend: {
                display: false
            },
            onClick: function(evt) {
                if (that.cognitionEnd === 0) {
                    const activePoints = this.getElementsAtEvent(evt);
                    const chartData = activePoints[0]['_chart'].config.data;
                    const idx = activePoints[0]['_index'];

                    const value = chartData.datasets[0].data[idx];
                    that.isMatch = that.targetValue === value ? 1 : 0;
                    that.cognitionEnd = Date.now();
                    console.log("Matched: " + that.isMatch + ", Time: " + (that.cognitionEnd - that.cognitionStart));
                    that.user = prompt("Timer stops! Please enter your worker id:", "worker");
                    that.send();
                }
            },
            tooltips: {
                callbacks: {
                    label: (tooltipItem) => {
                        const label = tooltipItem["label"];
                        const title = plot["title"];
                        return title.replace("?", label);
                    },
                    title: () => {}
                },
                displayColors: false
            }
        };
        return new Chart(ctx, {
            type: 'bar',
            data: data,
            options: options
        });
    }

    showTooltip(chart, index) {
        const segment = chart.getDatasetMeta(0).data[index];
        chart.tooltip._active = [segment];
        chart.tooltip.update();
        chart.draw();
    }

    highlightBar(charts, targetRank) {
        // this clears off any tooltip highlights
        _.each(charts, barchart => {
            let targetIndex = -1;
            // reset any coloring because of selection
            barchart.data.datasets.forEach( (dataset) => {
                dataset.backgroundColor = _.map(dataset.backgroundColor, (color, idx) => {
                    const rank = dataset.rank[idx];
                    const newColor = rank !== undefined ? this.colorGradient.getColor(rank + 1) : "#0000ff";
                    const alpha = targetRank === rank ? 1 : 0.3;
                    if (targetRank === rank) {
                        targetIndex = idx;
                    }
                    return this.hexToRgbA(newColor, alpha);
                });
            });
            barchart.update();
            if (targetIndex >= 0) {
                this.showTooltip(barchart, targetIndex);
            }
        });
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
                        this.user = prompt("Timer stops! Please enter your worker id:", "worker");
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
            axisY:{
                minimum: 40,
                maximum: 100
            }
        });
    }

    createModal() {
        const imagePath = this.colorPos >= 0 ? "/images/color.png" : "/images/overview.png";
        const suggestHovering = this.colorPos < 0 ? "" : ("Bars of left colors are more likely to match with the target query. " +
            "You can hover on the color gradiant to highlight bars with the selected color.<br>");
        $("#body").append(
            `
               <div class="ui large modal" id="target_query">
                    <div class="header">
                       Find the Target Query: <span style="color:red; font-size: 20px; font-weight: bold;">`
                                + $("#spoken-text").val() + `</span> <br>
                    </div>
                    <div>
                        <div class="description" style="padding-left: 100px; padding-top: 20px">
                          <p>
                          ` + suggestHovering + `
                                One example is shown below:
                          </p>
                        </div>
                    </div>
                    <div class="image content">
                        <div class="ui massive image">
                          <img src="` + imagePath + `">
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
        $("#target_query")
            .modal({
                onHide: () => {
                    if (this.cognitionStart === 0) {
                        alert("Timer starts after clicking OK!");
                        this.cognitionStart = Date.now();
                    }
                },
            }).modal("show");
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

    send() {
        const respond = this.cognitionEnd - this.cognitionStart;
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