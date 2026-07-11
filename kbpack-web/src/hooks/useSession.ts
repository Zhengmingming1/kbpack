import { queryOptions, useQuery } from '@tanstack/react-query';
import { me } from '../api/auth';

export const sessionQueryOptions = queryOptions({
  queryKey: ['auth', 'me'],
  queryFn: me,
  retry: false,
  staleTime: 5 * 60 * 1000,
});

export function useSession() {
  return useQuery(sessionQueryOptions);
}
