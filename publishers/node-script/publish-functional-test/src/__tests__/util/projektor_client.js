const axios = require("axios");

const fetchTestRunSummary = (testRunId, serverPort) => {
  const url = `http://localhost:${serverPort}/run/${testRunId}/summary`;

  return axios.get(url);
};

const fetchAttachments = (testRunId, serverPort) =>
  axios.get(`http://localhost:${serverPort}/run/${testRunId}/attachments`);

const fetchAttachment = (attachmentName, testRunId, serverPort) => {
  const url = `http://localhost:${serverPort}/run/${testRunId}/attachments/${attachmentName}`;

  return axios.get(url);
};

module.exports = {
  fetchTestRunSummary,
  fetchAttachments,
  fetchAttachment,
};
