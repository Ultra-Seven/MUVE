import _ from "underscore";
import Barchart from "../viz/barchart";
import Connector from "./connector";

class Engine {
    constructor(config) {
        this.render_data = [];
        const AJAX = config.ajax;
        let params;
        const route = "/lucene/";
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
                callback: (msg) => {
                    console.log("Receiving data");
                    const data = JSON.parse(msg.data);
                    // $("#answer").html(data);
                    this.render_data = data["data"];
                    this.renderTime = 0;
                    const template = data["debug"]
                    if (this.render_data.length === 0) {
                        $("#viz").empty();
                        $("#viz_title").html("No Results for " + JSON.stringify(template));
                    }
                    else {
                        this.draw(this.render_data);
                    }
                },
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
            $("#spoken-text").val(sentences);
            const params = [name, sentences, $("#viz").width(), $("#planner").val()];
            this.connector.send(params.join(";"));
        };
    }

    simulate(sql) {
        const name = $('#datasets').val();
        const params = [name, sql, $("#viz").width(), $("#planner").val()];
        this.connector.send(params.join(";"));
    }

    draw(query_results) {
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
        const scales = charts[0].colorScales;
        _.each(scales, (color, idx) => {
            const id = "color_" + idx;
            $("#legend").append("<button class='ui button' id='" + id +"'></button>");
            $("#" + id).css("background-color", color);
            $("#" + id).width("80px");
            $("#" + id).height("30px");
        });
        $("#color_0").html("High").css('color', 'white').css("fontSize", 20);
        $("#color_" + (scales.length - 1)).html("Low").css('color', 'white').css("fontSize", 20);
        // Remove watermarks
        $('.canvasjs-chart-credit').remove();
    }
}
export default Engine;