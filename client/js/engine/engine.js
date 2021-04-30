import _ from "underscore";
import Barchart from "../viz/barchart";
import Connector from "./connector";
import PlotSender from "./plotSender";
import ApproximateSender from "./approxSender";
import DefaultSender from "./defaultSender";
import BackSender from "./backSender";

class Engine {
    constructor(config) {
        this.render_data = [];
        this.charts = {};
        this.current_time = 0;
        const AJAX = config.ajax;
        this.sender = {
            "default": new DefaultSender(this),
            "incremental": new PlotSender(this),
            "approximate": new ApproximateSender(this),
            "backup": new BackSender(this)
        }
        let params;
        const route = /stream/;
        if (AJAX) {
            params = {
                sender: "AJAX",
                callback: (resultData) => {
                    console.log("Receiving data");
                    this.render_data = JSON.parse(resultData)
                    // $("#answer").html(data);
                    this.draw(this.render_data)
                },
                url: config.host + route
            };
        }
        else {
            params = {
                sender: "WS",
                callback: this.sender["default"].callback,
                url: config.host + route
            };
        }
        this.connector = new Connector(params);

        this.sendHandler = (event) => {
            if (event.results) {
                $("#spoken-text").val(event.results[0][0].transcript);
            }
            const sentences = $("#spoken-text").val();
            const name = $('#datasets').val();
            const presenter = $('#present').val();
            this.connector.sender.setCallback(this.sender[presenter].callback);
            $("#spoken-text").val(sentences);
            const params = [name, sentences, $("#viz").width(), $("#planner").val(), Date.now(), presenter];
            this.connector.send(params.join(";"));
        };
    }

    simulate(sql) {
        const name = $('#datasets').val();
        const params = [name, sql, $("#viz").width(), $("#planner").val()];
        this.connector.send(params.join(";"));
    }

    drawChartJS(query_results) {
        $("#viz").empty();
        $("#legend").empty();
        const charts = [];
        _.each(query_results, (row, idx) => {
            const groupBys = Object.keys(row);
            const nrFigures = groupBys.length;
            const rowName = "row_" + idx;
            $("#viz").append("<div id='" + rowName + "'></div>");
            for (let figureCtr = 0; figureCtr < nrFigures; figureCtr++) {
                const barName = "bar_" + idx + "_" + figureCtr;
                const group = groupBys[figureCtr];
                const width = row[group]["width"];
                const data = row[group]["data"];
                $("#" + rowName).append(
                    "<div id='" + barName +
                    "' style='width: " + width + "%; height: 350px;display: inline-block;'></div>"
                );
                $("#" + barName).append("<canvas id='canvas_" + barName + "'></canvas>");

                const barChart = new Barchart(barName, this);
                charts.push(barChart);
                if (data.length > 0) {
                    barChart.drawBarChartUsingChartJS(data);
                }
                // Title name
                // const key = Object.keys(data[0]["results"])[0];
                // const key_arr = _.filter(key.split(/[()]+/), element => element !== "");
                // const aggTitle = key_arr[0];
                // const target = (key_arr.length === 1 ?
                //     key_arr[0] : key_arr[1]).replaceAll("_"," ");
                // let aggPrefix;
                // if (aggTitle.startsWith("max")) {
                //     aggPrefix = "Maximum of ";
                // }
                // else if (aggTitle.startsWith("min")) {
                //     aggPrefix = "Minimum of ";
                // }
                // else if (aggTitle.startsWith("sum")) {
                //     aggPrefix = "Sum of ";
                // }
                // else if (aggTitle.startsWith("avg")) {
                //     aggPrefix = "Average of ";
                // }
                // else {
                //     aggPrefix = "Count of ";
                // }
                // $("#viz_title").html("Visualizations for " + aggPrefix + target);
            }

        });

        // Remove watermarks
        $('.canvasjs-chart-credit').remove();
    }

    draw(query_results, colors) {
        $("#viz").empty();
        $("#legend").empty();
        const charts = [];
        _.each(query_results, (row, idx) => {
            const groupBys = Object.keys(row);
            const nrFigures = groupBys.length;
            const rowName = "row_" + idx;
            $("#viz").append("<div id='" + rowName + "'></div>");
            for (let figureCtr = 0; figureCtr < nrFigures; figureCtr++) {
                const barName = "bar_" + idx + "_" + figureCtr;
                const group = groupBys[figureCtr];
                const width = row[group]["width"];
                const data = row[group]["data"];
                $("#" + rowName).append(
                    "<div id='" + barName +
                    "' style='width: " + width + "%; height: 280px;display: inline-block;'></div>"
                );
                const barChart = new Barchart(barName, this);
                charts.push(barChart);
                barChart.drawBarChart(data, group);
                // Title name
                const key = Object.keys(data[0]["results"])[0];
                const key_arr = _.filter(key.split(/[()]+/), element => element !== "");
                const aggTitle = key_arr[0];
                const target = (key_arr.length === 1 ?
                    key_arr[0] : key_arr[1]).replaceAll("_"," ");
                let aggPrefix;
                if (aggTitle.startsWith("max")) {
                    aggPrefix = "Maximum of ";
                }
                else if (aggTitle.startsWith("min")) {
                    aggPrefix = "Minimum of ";
                }
                else if (aggTitle.startsWith("sum")) {
                    aggPrefix = "Sum of ";
                }
                else if (aggTitle.startsWith("avg")) {
                    aggPrefix = "Average of ";
                }
                else {
                    aggPrefix = "Count of ";
                }
                $("#viz_title").html("Visualizations for " + aggPrefix + target);
            }

        });
        const scales = colors || charts[0].colorScales;

        _.each(scales, (color, idx) => {
            const id = "color_" + idx;
            $("#legend").append("<button class='ui button' id='" + id +"'></button>");
            $("#" + id).css("background-color", color);
            $("#" + id).width("10px");
            $("#" + id).height("30px");
        });
        $("#color_0").html("High").css('color', 'white').css("fontSize", 20);
        $("#color_" + (scales.length - 1)).html("Low").css('color', 'white').css("fontSize", 20);
        // Remove watermarks
        $('.canvasjs-chart-credit').remove();
    }

    createDivs() {
        $("#viz").empty();
        _.each(this.render_data, (rowData, idx) => {
            const rowName = "row_" + idx;
            $("#viz").append("<div id='" + rowName + "'></div>");
            _.each(rowData, plotSpec => {
                const barName = plotSpec["name"];
                const width = plotSpec["width"];
                $("#" + rowName).append(
                    "<div id='" + barName +
                    "' style='width: " + width + "%; height: 350px;display: inline-block;'></div>"
                );
                $("#" + barName).append("<canvas id='canvas_" + barName + "'></canvas>");
            });
        });
    }

    createApproximation() {
        $("#viz").empty();
        _.each(this.render_data, (rowData, idx) => {
            const rowName = "row_" + idx;
            $("#viz").append("<div id='" + rowName + "'></div>");
            _.each(rowData, plotSpec => {
                const barName = plotSpec["name"];
                const width = plotSpec["width"];
                const results = plotSpec["result"];
                $("#" + rowName).append(
                    "<div id='" + barName +
                    "' style='width: " + width + "%; height: 350px;display: inline-block;'></div>"
                );
                $("#" + barName).append("<canvas id='canvas_" + barName + "'></canvas>");
                const barChart = new Barchart(barName, this);
                barChart.drawPlotChartJS(results);
                this.charts[barName] = barChart;
            });
        });
    }

    updatePlotChartJS(query_results, name) {
        this.charts[name].updatePlotChartJS(query_results);
    }

    drawPlotChartJS(query_results, name) {
        const barChart = new Barchart(name, this);
        barChart.drawPlotChartJS(query_results);
    }

    append_debug(debug_data) {
        const date = new Date();
        const time = "[" + date.toUTCString() + "]";
        const query = " SQL: " + debug_data["query"];
        const setup = "[Setup] Planner: " + debug_data["planner"] + ", Rows: " + debug_data["rows"];
        const performance = "[Performance] Matching Time: " + debug_data["searchMillis"]
            + " ms, Planning Time: " + debug_data["planMillis"] + " ms";
        const performance_2 = "[Performance] Execution Time: " + debug_data["executionMillis"]
            + " ms, Nr. Queries: " + debug_data["nrQueries"];
        $("#detailed_response").append("<p>" + time + query + "<\p>").append("<p>" + setup + "<\p>")
            .append("<p>" + performance + "<\p>").append("<p>" + performance_2 + "<\p>");
    }
}
export default Engine;