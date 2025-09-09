import axios from 'axios';
import { ExpenseAPI } from './expenses';
import { DebtAPI } from './debts';

const api = axios.create({
  baseURL: '/api',
  timeout: 10000
});

export default api;
export { ExpenseAPI, DebtAPI };
