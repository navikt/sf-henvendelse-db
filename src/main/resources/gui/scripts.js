let currentPage = 1;
let pageSize = 1;
let count = 0;
let pageCount = 1;
let authToken = '';
let countdownInterval;

window.onload = function() {
    console.log("Do on load");
    loadTablePage(currentPage);
};

const loadTablePage = async (page) => {
    const response = await fetch('/internal/view?page=' + page, {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json'
        }
    });

    if (response.status === 401) {
        // Unauthorized
        document.getElementById('authorization-message').innerHTML = 'Unauthorized. <button onclick="login()">Login</button>';
        return;
    }

    const data = await response.json();
    const records = data.records;
    currentPage = data.page
    pageCount = data.pageCount
    pageSize = data.pageSize
    count = data.count

    const nameInfo = document.getElementById('name-info');

    nameInfo.textContent = data.username

    updateExpirationTime(data.expireTime);

    // Display records in a table
    const tableContainer = document.getElementById('table-container');

    tableContainer.innerHTML = '<table class="blueTable">' +
        '<thead><tr><th id="idColumn">KjedeId</th><th id="aktoridColumn">AktorId</th><th id="fnrColumn">Fnr</th><th>JSON</th><th id ="lastmodifiedColumn">Last Modified</th><th id="bySFColumn">By SF</th></tr></thead><tbody>' +
        records.map(record =>
            `<tr>
                    <td>${record.kjedeId}</td>
                    <td>${record.aktorId}</td>
                    <td>${record.fnr}</td>
                    <td class="json-td" onclick="showJsonPopup(event)">${record.json}</td>
                    <td>${record.lastModified}</td>
                    <td>${record.lastModifiedBySF}</td>
                </tr>`
        ).join('') +
        '</tbody>' +
        '<tfoot>' +
        '<tr><td colspan="6"><span id="sampletext"></span></td></tr>' +
        '<tr><td colspan="6"><div class="links"><a onclick="loadFirstPage()">&laquo;</a><a onclick="loadPreviousPage()">&lsaquo;</a>' +
        '<span id="currentPage"></span><a onclick="loadNextPage()">&rsaquo;</a><a onclick="loadLastPage()">&raquo;</a></div></td>' +
        '</tr>' +
        '</tfoot>'
    '</table>';

    // Update current page information
    const sampleTextElement = document.getElementById('sampletext');
    const currentPageElement = document.getElementById('currentPage');
    sampleTextElement.textContent = `Showing ${records.length} of ${count} records`;
    currentPageElement.textContent = `Page ${currentPage} of ${pageCount}`;

    document.getElementById('authorization-message').textContent = '';
};

const loadPreviousPage = () => {
    if (currentPage > 1) {
        loadTablePage(currentPage - 1);
    }
};

const loadFirstPage = () => {
    loadTablePage(1);
};

const loadLastPage = () => {
    loadTablePage(pageCount);
};

const refreshTable = () => {
    loadTablePage(currentPage); // Reload the current page
};

const loadNextPage = () => {
    // You should check if there are more pages before loading the next one
    if (currentPage < pageCount) {
        loadTablePage(currentPage + 1);
    }
};

const login = () => {
    window.location.href = '/internal/login';
}

const logout = () => {
    window.location.href = '/internal/logout';
}

var isPopupShowing = false;
// Function to show a popup with prettified JSON data
const showJsonPopup = (event) => {
    // If a popup is already showing, do nothing
    if (isPopupShowing) {
        return;
    }
    var tdElement = event.target;
    var jsonData = tdElement.textContent;

    // Highlight the clicked <td> element
    tdElement.classList.add('highlighted');

    isPopupShowing = true;
    // Create a popup element
    const popup = document.createElement('div');
    popup.classList.add('json-popup');

    // Create a close button
    const closeButton = document.createElement('span');
    closeButton.classList.add('close-button');
    closeButton.textContent = '(X)';
    closeButton.onclick = () => {
        // Reset the flag to false to indicate that the popup is closed
        isPopupShowing = false;
        // Remove the highlighting from all <td> elements
        var tds = document.querySelectorAll('.json-td');
        tds.forEach(function(td) {
            td.classList.remove('highlighted');
        });
        // Remove the popup when the close button is clicked
        popup.remove();
    };

    // Create a pre element to display the prettified JSON data
    const jsonContent = document.createElement('pre');
    jsonContent.textContent = JSON.stringify(JSON.parse(jsonData), null, 2);

    // Append the close button and JSON content to the popup
    popup.appendChild(closeButton);
    popup.appendChild(jsonContent);

    // Append the popup to the document body
    document.body.appendChild(popup);
};

const updateExpirationTime = (expireTime) => {
    const expireInfo = document.getElementById('expire-info');

    const updateTime = () => {
        const currentTime = Date.now();
        const timeLeft = expireTime - currentTime;

        if (timeLeft > 0) {
            const minutes = Math.floor(timeLeft / 60000);
            const seconds = Math.floor((timeLeft % 60000) / 1000);
            expireInfo.textContent = `${minutes} minutes and ${seconds} seconds remaining`;
        } else {
            expireInfo.textContent = 'Token has expired';
            clearInterval(interval);
        }
    };

    // Initial call to display time immediately
    updateTime();

    // Update every second
    const interval = setInterval(updateTime, 1000);
};