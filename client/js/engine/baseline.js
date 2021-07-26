import Connector from "./connector";
import _ from "underscore";
class Baseline {
    constructor(config) {
        this.render_data = [];
        const showCandidates = config["showCandidates"] || false
        const route = showCandidates ? "/dataTone/" : "/best/";
        const params = {
            sender: "WS",
            callback: (msg) => {
                console.log("Receiving data");
                this.render_data = JSON.parse(msg.data);
                if (Object.keys(this.render_data).length === 0) {

                }
                else {
                    if (showCandidates) {
                        this.draw(this.render_data);
                    }
                    else {
                        this.drawResults(this.render_data);
                    }
                }
                this.renderTime = Date.now();
            },
            url: config.host + route
        };
        const ajaxRoute = "/query/";
        const ajaxParams = {
            sender: "AJAX",
            callback: (resultData) => {
                const data = JSON.parse(resultData);
                if (showCandidates) {
                    if (data.length > 0) {
                        $("#output").empty();
                        let tableElement = "<table class=\"ui table\"><thead>" +
                            "<tr><th>Row</th><th>Result</th></tr></thead><tbody>";
                        _.each(data, (row, idx) => {
                            tableElement += "<tr><td>" + idx + "</td><td>" + row + "</td></tr>";
                        });
                        tableElement += "</tbody></table>";
                        $("#output").append(tableElement);
                    }
                    else {
                        $("#output").empty();
                        $("#output").append("<p>No results</p>");
                    }
                }
                else {
                    console.log("")
                }
                this.end = Date.now();
            },
            url: config.host + ajaxRoute
        }


        this.connector = new Connector(params);
        this.queryConnector = new Connector(ajaxParams);

        this.sendHandler = (event) => {
            if (event.results) {
                $("#spoken-text").val(event.results[0][0].transcript);
            }
            const sentences = $("#spoken-text").val();
            const name = $('#datasets').val();
            $("#spoken-text").val(sentences);
            console.log(sentences);
            const params = [name, sentences];
            if (sentences.length > 0) {
                this.connector.send(params.join(";"));
            }
        };

        $("#btn-submit-query").click(() => {
            const curOp = $("#curOps").html();
            const curSelect = $("#curSelects").html();
            const curColumn = $("#curColumns").html();
            const curVal = $("#curValues").html();
            const name = $('#datasets').val();
            if (curOp) {
                let sql = "select ";
                const func = this.sqlOp(curOp);
                const selectItem = curSelect === "*" ? curSelect : "\"" + curSelect + "\"";
                sql += func + "(" + selectItem + ") from " + name +
                    " where " + curColumn + "='" + curVal + "';";
                this.queryConnector.send(sql);
            }
            else {
                console.log("No query!")
            }
        });
    }

    simulate(sql) {
        const name = $('#datasets').val();
        const params = [name, sql];
        this.connector.send(params.join(";"));
    }

    sqlOp(curOp) {
        switch (curOp) {
            case "Count": return "count";
            case "Maximum": return "max";
            case "Minimum": return "min";
            case "Sum": return "sum";
            case "Average": return "avg";
            default: return "";
        }
    }

    draw(results) {
        const ops = results["ops"];
        const selects = results["selects"];
        const columns = results["columns"];
        const values = results["values"];

        const curOp = ops[0];
        const curSelect = selects[0];
        const curColumn = columns[0];
        const curVal = values[0];

        $("#query_interaction").empty();
        $("#query_interaction").append("<a class=\"ui grey label Large\" " +
            "style=\"display: flex;align-items: center;\">Show me the</a>");

        // Add operators dropdown menu
        $("#query_interaction").append(this.buildDropdown("curOps", curOp,
            ops, ["#fff6f6", "#e0b4b4", "#9f3a38"]));
        $("#query_interaction").append("<a class=\"ui grey label Large\" " +
            "style=\"display: flex;align-items: center;\">of</a>");
        $("#query_interaction").append(this.buildDropdown("curSelects", curSelect,
            selects, ["#fae7ba", "#f5ee51", "#000000"]));
        $("#query_interaction").append("<a class=\"ui grey label Large\" " +
            "style=\"display: flex;align-items: center;\">where</a>");
        $("#query_interaction").append(this.buildDropdown("curColumns", curColumn,
            columns, ["#fae7ba", "#f5ee51", "#000000"]));
        $("#query_interaction").append("<a class=\"ui grey label Large\" " +
            "style=\"display: flex;align-items: center;\">=</a>");
        $("#query_interaction").append(this.buildDropdown("curValues", curVal,
            values, ["#d2fdb9", "#6cfa1b", "#2e8100"]));
        $(".select-query").dropdown();
    }

    drawResults(results) {
        const sql = results["sql"];
        const queryResults = results["result"];

        $("#viz").empty();
        $("#viz").append("<a class=\"ui grey label Large\" " +
            "style=\"display: flex;align-items: center;\">" + sql + "</a>");
        $("#viz").append("<a class=\"ui grey label Large\" " +
            "style=\"display: flex;align-items: center;\">" + queryResults + "</a>");
    }

    buildDropdown(name, val, list, colors) {
        let htmlElement = "<div class=\"ui scrolling selection dropdown select-query\" " +
            "style='min-width: 150px;background-color: " + colors[0] + ";border-color: " + colors[1] + ";'>";
        htmlElement += "<input name=\"" + name + "\" type=\"hidden\" value=\"" + val + "\">";
        htmlElement += "<i class=\"dropdown icon\"></i>";
        htmlElement += "<div class=\"text\" id=\"" + name + "\" style='color: " + colors[2] + ";white-space: pre-wrap;" +
            "word-break: break-word;'>" + val + "</div>";
        htmlElement += "<div class=\"menu ui transition hidden\">";
        _.each(list, element => {
            htmlElement += "<div class=\"item\" data-value=\"" + element + "\">" + element + "</div>";
        });
        htmlElement += "</div></div>";
        return htmlElement;
    }
}
export default Baseline;