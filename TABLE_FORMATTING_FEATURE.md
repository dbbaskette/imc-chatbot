# Table Formatting Feature

## Overview

The IMC Chatbot now supports structured table responses that can be rendered as HTML tables in your display client. When the LLM detects that it should present tabular data, it will format the response in a structured JSON format that your client can parse and render.

## How It Works

### 1. **LLM Decision Making**
The LLM is instructed via the system prompt to format tabular data (like policy lists, customer information, or claim details) as structured JSON.

### 2. **Response Parsing**
The `ResponseParserService` automatically detects when the LLM returns structured data and parses it accordingly.

### 3. **Structured Response Format**
Your client receives a `ChatResponse` with a structured `response` field that can contain either:
- **Text responses**: Regular string content
- **Table data**: Structured JSON with columns and data rows

## Response Format Examples

### **Text Response (Regular)**
```json
{
  "response": "Hello! How can I help you with your insurance policy today?",
  "sessionId": "web-abc123-1234567890",
  "timestamp": 1692556800000
}
```

### **Table Response (Structured)**
```json
{
  "response": {
    "type": "dataTable",
    "data": [
      {"Policy": "AUTO-001", "Status": "Active", "Premium": "$150/month"},
      {"Policy": "HOME-002", "Status": "Active", "Premium": "$75/month"},
      {"Policy": "LIFE-003", "Status": "Pending", "Premium": "$200/month"}
    ],
    "columns": ["Policy", "Status", "Premium"]
  },
  "sessionId": "web-abc123-1234567890",
  "timestamp": 1692556800000
}
```

## Client-Side Implementation

### **Detecting Response Type**
```javascript
const response = await fetch('/api/chat', { ... });
const data = await response.json();

if (typeof data.response === 'string') {
    // Regular text response
    displayText(data.response);
} else if (data.response.type === 'dataTable') {
    // Table data - render as HTML table
    renderTable(data.response.data, data.response.columns);
} else {
    // Fallback
    displayText('Response received');
}
```

### **Rendering Table**
```javascript
function renderTable(data, columns) {
    const table = document.createElement('table');
    
    // Create header
    const thead = document.createElement('thead');
    const headerRow = document.createElement('tr');
    columns.forEach(column => {
        const th = document.createElement('th');
        th.textContent = column;
        headerRow.appendChild(th);
    });
    thead.appendChild(headerRow);
    table.appendChild(thead);
    
    // Create body
    const tbody = document.createElement('tbody');
    data.forEach(row => {
        const tr = document.createElement('tr');
        columns.forEach(column => {
            const td = document.createElement('td');
            td.textContent = row[column] || '';
            tr.appendChild(td);
        });
        tbody.appendChild(tr);
    });
    table.appendChild(tbody);
    
    // Add to DOM
    document.getElementById('chat-container').appendChild(table);
}
```

## When Tables Are Generated

The LLM will automatically format responses as tables when:
- **Policy lists** are requested
- **Customer information** is displayed
- **Claim details** are shown
- **Any structured data** that would benefit from tabular presentation

## Fallback Behavior

- If the LLM doesn't format data as a table, it falls back to regular text
- If there's an error parsing structured data, it falls back to text
- The system maintains backward compatibility with existing text-only responses

## Configuration

The table formatting behavior is controlled by the system prompt in `application-mcp.properties`:

```properties
imc.chatbot.system-prompt=... \
IMPORTANT: When presenting tabular data (like policy lists, customer information, or claim details), format your response as structured JSON with the following format: \
{ \
  "type": "dataTable", \
  "data": [ \
    {"Column1": "value1", "Column2": "value2"}, \
    {"Column1": "value3", "Column2": "value4"} \
  ], \
  "columns": ["Column1", "Column2"] \
} \
...
```

## Benefits

1. **Better Data Presentation**: Tables are much more readable than plain text
2. **Structured Data**: Your client can sort, filter, and manipulate the data
3. **Consistent Format**: Standardized JSON structure for all table data
4. **Backward Compatible**: Existing text responses continue to work
5. **Automatic Detection**: No need to manually specify when to use tables

## Testing

You can test the feature by asking questions that would naturally return tabular data:
- "Show me all my active policies"
- "List my recent claims"
- "What are the details of my customer profile?"

The LLM should automatically format these responses as structured tables.
