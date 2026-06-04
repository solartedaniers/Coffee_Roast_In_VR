import apiClient, { getErrorMessage } from './apiClient';

const KNOWLEDGE_LEVEL_PATH = '/users/me/knowledge-level';

export async function updateKnowledgeLevel(knowledgeLevel) {
  try {
    const response = await apiClient.patch(KNOWLEDGE_LEVEL_PATH, { knowledgeLevel });
    return response.data;
  } catch (error) {
    throw new Error(getErrorMessage(error));
  }
}
